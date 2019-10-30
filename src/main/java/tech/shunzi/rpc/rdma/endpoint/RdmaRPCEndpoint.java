package tech.shunzi.rpc.rdma.endpoint;

import com.ibm.disni.RdmaEndpoint;
import com.ibm.disni.verbs.*;
import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RdmaRPCEndpoint<Q extends RdmaRPCMessage, P extends RdmaRPCMessage> extends RdmaEndpoint {
    private static final int headerSize = 4;

    private RdmaRPCEndpointGroup<? extends RdmaRPCEndpoint<Q,P>, Q, P> rpcGroup;
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

    protected RdmaRPCEndpoint(RdmaRPCEndpointGroup<? extends RdmaRPCEndpoint<Q,P>, Q, P> group, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(group, idPriv, serverSide);
        this.rpcGroup = group;
        this.maxinline = rpcGroup.getMaxInline();
        this.payloadSize = rpcGroup.getBufferSize();
        this.rawBufferSize = headerSize + this.payloadSize;
        this.pipelineLength = rpcGroup.getRecvQueueSize();
        this.freePostSend = new ArrayBlockingQueue<SVCPostSend>(pipelineLength);
        this.pendingPostSend = new ConcurrentHashMap<Integer, SVCPostSend>();
        this.recvBufs = new ByteBuffer[pipelineLength];
        this.sendBufs = new ByteBuffer[pipelineLength];
        this.recvCall = new SVCPostRecv[pipelineLength];
        this.sendCall = new SVCPostSend[pipelineLength];
        this.ticketCount = new AtomicLong(0);
        this.messagesSent = new AtomicLong(0);
        this.messagesReceived = new AtomicLong(0);
    }

    public boolean sendMessage(RdmaRPCMessage message, int ticket) throws IOException {

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
            return true;
        } else {
            return false;
        }
    }
}
