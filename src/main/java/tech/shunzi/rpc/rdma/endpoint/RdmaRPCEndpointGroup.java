package tech.shunzi.rpc.rdma.endpoint;

import com.ibm.disni.RdmaCqProvider;
import com.ibm.disni.RdmaEndpointGroup;
import com.ibm.disni.verbs.IbvQP;
import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;
import tech.shunzi.rpc.rdma.common.RdmaRPCProtocol;

import java.io.IOException;

public abstract class RdmaRPCEndpointGroup<E extends RdmaRPCEndpoint<Q, P>, Q extends RdmaRPCMessage, P extends RdmaRPCMessage> extends RdmaEndpointGroup<E> {

    private int recvQueueSize;
    private int sendQueueSize;
    private int bufferSize;
    private int maxInline;
    private int timeout;

    public RdmaRPCEndpointGroup(RdmaRPCProtocol<Q,P> protocol, int timeout, int maxinline, int recvQueue, int sendQueue) throws IOException {
        super(timeout);
        this.timeout = timeout;
        this.recvQueueSize = recvQueue;
        this.sendQueueSize = Math.max(recvQueue, sendQueue);
        this.bufferSize = Math.max(protocol.createRequest().size(), protocol.createResponse().size());
        this.maxInline = maxinline;
    }

    public int getRecvQueueSize() {
        return recvQueueSize;
    }

    public void setRecvQueueSize(int recvQueueSize) {
        this.recvQueueSize = recvQueueSize;
    }

    public int getSendQueueSize() {
        return sendQueueSize;
    }

    public void setSendQueueSize(int sendQueueSize) {
        this.sendQueueSize = sendQueueSize;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getMaxInline() {
        return maxInline;
    }

    public void setMaxInline(int maxInline) {
        this.maxInline = maxInline;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
