/*
 * DaRPC: Data Center Remote Procedure Call
 *
 * Author: Patrick Stuedi <stu@zurich.ibm.com>
 *
 * Copyright (C) 2016-2018, IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.homework.darpc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.disni.verbs.*;
import com.ibm.disni.util.MemoryUtils;
import com.ibm.disni.*;

public abstract class DaRPCEndpoint<R extends DaRPCMessage, T extends DaRPCMessage> extends RdmaEndpoint {
	private static final Logger logger = LoggerFactory.getLogger("com.ibm.darpc");
	private static final int headerSize = 4;
	
	public abstract void dispatchReceive(ByteBuffer buffer, int ticket, int recvIndex) throws IOException;
	public abstract void dispatchSend(int ticket) throws IOException;
	
	private DaRPCEndpointGroup<? extends DaRPCEndpoint<R,T>, R, T> rpcGroup;
	private ByteBuffer dataBuffer;
	private IbvMr dataMr;
	private ByteBuffer receiveBuffer;
	private ByteBuffer sendBuffer;
	private ByteBuffer[] recvBufs;
	private ByteBuffer[] sendBufs;
	private SVCPostRecv[] recvCall;
	private SVCPostSend[] sendCall;
	private ConcurrentHashMap<Integer, SVCPostSend> pendingPostSend;
	private ArrayBlockingQueue<SVCPostSend> freePostSend;
	private AtomicLong ticketCount;
	private int pipelineLength;
	private int payloadSize;
	private int rawBufferSize;
	private int maxinline;
	private AtomicLong messagesSent;
	private AtomicLong messagesReceived;
	
	
	public DaRPCEndpoint(DaRPCEndpointGroup<? extends DaRPCEndpoint<R,T>, R, T> endpointGroup, RdmaCmId idPriv, boolean serverSide) throws IOException {
		super(endpointGroup, idPriv, serverSide);
		this.rpcGroup = endpointGroup;
		this.maxinline = rpcGroup.getMaxInline();
		this.payloadSize = rpcGroup.getBufferSize();
		this.rawBufferSize = headerSize + this.payloadSize;
		this.pipelineLength = rpcGroup.recvQueueSize();
		this.freePostSend = new ArrayBlockingQueue<SVCPostSend>(pipelineLength);
		this.pendingPostSend = new ConcurrentHashMap<Integer, SVCPostSend>();
		this.recvBufs = new ByteBuffer[pipelineLength];
		this.sendBufs = new ByteBuffer[pipelineLength];
		this.recvCall = new SVCPostRecv[pipelineLength];
		this.sendCall = new SVCPostSend[pipelineLength];
		this.ticketCount = new AtomicLong(0);
		this.messagesSent = new AtomicLong(0);
		this.messagesReceived = new AtomicLong(0);
		logger.info("RPC client endpoint, with payload buffer size = " + payloadSize + ", pipeline " + pipelineLength);
	}

	/**
	 * Register Memory and Allocate buffer region for send and receive.
	 * @throws IOException
	 */
	public void init() throws IOException {
		int sendBufferOffset = pipelineLength * rawBufferSize;

		/* Main data buffer for sends and receives. Will be split into two regions,
		 * one for sends and one for receives.
		 */
		dataBuffer = ByteBuffer.allocateDirect(pipelineLength * rawBufferSize * 2);
		/* Only do one memory registration with the IB card. */
		dataMr = registerMemory(dataBuffer).execute().free().getMr();

		/* Receive memory region is the first half of the main buffer. */
		dataBuffer.limit(dataBuffer.position() + sendBufferOffset);
		receiveBuffer = dataBuffer.slice();

		/* Send memory region is the second half of the main buffer. */
		dataBuffer.position(sendBufferOffset);
		dataBuffer.limit(dataBuffer.position() + sendBufferOffset);
		sendBuffer = dataBuffer.slice();

		for(int i = 0; i < pipelineLength; i++) {
			/* Create single receive buffers within the receive region in form of slices. */
			receiveBuffer.position(i * rawBufferSize);
			receiveBuffer.limit(receiveBuffer.position() + rawBufferSize);
			recvBufs[i] = receiveBuffer.slice();

			/* Create single send buffers within the send region in form of slices. */
			sendBuffer.position(i * rawBufferSize);
			sendBuffer.limit(sendBuffer.position() + rawBufferSize);
			sendBufs[i] = sendBuffer.slice();

			this.recvCall[i] = setupRecvTask(i);
			this.sendCall[i] = setupSendTask(i);
			freePostSend.add(sendCall[i]);
			recvCall[i].execute();
		}
	}

    /**
     * Deregister the memory
     * @throws IOException
     * @throws InterruptedException
     */
	@Override
	public synchronized void close() throws IOException, InterruptedException {
		super.close();
		deregisterMemory(dataMr);
	}	
	
	public long getMessagesSent() {
		return messagesSent.get();
	}
	
	public long getMessagesReceived() {
		return messagesReceived.get();
	}
	
	protected boolean sendMessage(DaRPCMessage message, int ticket) throws IOException {

		// Get the SVC object from blocking queue.
		SVCPostSend postSend = freePostSend.poll();
		if (null != postSend) {

			// Get the work-request id of first work request in this SVC object.
			int index = (int) postSend.getWrMod(0).getWr_id();

			// Write the ticket to send buffer
			sendBufs[index].putInt(0, ticket);

			// Move the pointer
			sendBufs[index].position(4);

			// Calculate the total length of byte buffer.
			int written = 4 + message.write(sendBufs[index]);

			// Set byte length for scatter/gather element of this work request.
			postSend.getWrMod(0).getSgeMod(0).setLength(written);

			// Set the flag for the work request with value IbvSendWR.IBV_SEND_SIGNALED | IbvSendWR.IBV_SEND_INLINE
			postSend.getWrMod(0).setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
			if (written <= maxinline) {
				postSend.getWrMod(0).setSend_flags(postSend.getWrMod(0).getSend_flags() | IbvSendWR.IBV_SEND_INLINE);
			}

			// Build the hash map with K-ticket V-SVC object
			pendingPostSend.put(ticket, postSend);

			// Execute the SVC object
			postSend.execute();

			// Calculate the number of sent message.
			messagesSent.incrementAndGet();

			// Return the result.
			return true;
		} else {
			return false;
		}
	}
	
	protected void postRecv(int index) throws IOException {
		recvCall[index].execute();
	}	
	
	public void freeSend(int ticket) throws IOException {
		SVCPostSend sendOperation = pendingPostSend.remove(ticket);
		if (sendOperation == null) {
			throw new IOException("no pending ticket " + ticket + ", current ticket count " + ticketCount.get());
		}
		this.freePostSend.add(sendOperation);
	}	
	
	public void dispatchCqEvent(IbvWC wc) throws IOException {
		if (wc.getStatus() == 5){
			//flush
			return;
		} else if (wc.getStatus() != 0){
			throw new IOException("Faulty operation! wc.status " + wc.getStatus());
		} 	
		
		if (wc.getOpcode() == 128){
			//receiving a message
			int index = (int) wc.getWr_id();
			ByteBuffer recvBuffer = recvBufs[index];
			int ticket = recvBuffer.getInt(0);
			recvBuffer.position(4);
			dispatchReceive(recvBuffer, ticket, index);
		} else if (wc.getOpcode() == 0) {
			//send completion
			int index = (int) wc.getWr_id();
			ByteBuffer sendBuffer = sendBufs[index];
			int ticket = sendBuffer.getInt(0);
			dispatchSend(ticket);
		} else {
			throw new IOException("Unkown opcode " + wc.getOpcode());
		}		
	}	
	
	private SVCPostSend setupSendTask(int wrid) throws IOException {
		ArrayList<IbvSendWR> sendWRs = new ArrayList<IbvSendWR>(1);
		LinkedList<IbvSge> sgeList = new LinkedList<IbvSge>();

		// Build scatter/gather element(Data Segment). Describes a local buffer.
		IbvSge sge = new IbvSge();
		// Set the start address and length for every slice
		sge.setAddr(MemoryUtils.getAddress(sendBufs[wrid]));
		sge.setLength(rawBufferSize);
		// Set the RDMA key for this task
		sge.setLkey(dataMr.getLkey());
		sgeList.add(sge);

		// Construct a send work request.
		IbvSendWR sendWR = new IbvSendWR();
		sendWR.setSg_list(sgeList);
		sendWR.setWr_id(wrid);
		sendWRs.add(sendWR);
		// Set the send flags with value IbvSendWR.IBV_SEND_SIGNALED
		sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
		// Set the opcode of this work request with IBV_WR_SEND.
		sendWR.setOpcode(IbvSendWR.IbvWrOcode.IBV_WR_SEND.ordinal());

        // post a list of work requests (WRs) to a send queue
		return postSend(sendWRs);
	}

	private SVCPostRecv setupRecvTask(int wrid) throws IOException {
		ArrayList<IbvRecvWR> recvWRs = new ArrayList<IbvRecvWR>(1);
		LinkedList<IbvSge> sgeList = new LinkedList<IbvSge>();

		// Build scatter/gather element(Data Segment). Describes a local buffer.
		IbvSge sge = new IbvSge();

		// Set the start address and length for every slice
		sge.setAddr(MemoryUtils.getAddress(recvBufs[wrid]));
		sge.setLength(rawBufferSize);
		// Set the RDMA key for this task
		sge.setLkey(dataMr.getLkey());
		sgeList.add(sge);

		// Construct a receive work request.
		IbvRecvWR recvWR = new IbvRecvWR();
		// Set the work request identifier
		recvWR.setWr_id(wrid);
		// Sets the scatter/gather elements of this work request. Each scatter/gather element refers to one single buffer.
		recvWR.setSg_list(sgeList);
		recvWRs.add(recvWR);

		// Post a receive operation on this endpoint.
        // post a list of work requests (WRs) to a receive queue
		return postRecv(recvWRs);
	}
}
