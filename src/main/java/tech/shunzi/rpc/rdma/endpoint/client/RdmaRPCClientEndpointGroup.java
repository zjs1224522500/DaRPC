package tech.shunzi.rpc.rdma.endpoint.client;

import com.ibm.disni.RdmaCqProvider;
import com.ibm.disni.verbs.IbvQP;
import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;
import tech.shunzi.rpc.rdma.common.RdmaRPCProtocol;
import tech.shunzi.rpc.rdma.endpoint.RdmaRPCEndpointGroup;

import java.io.IOException;

public class RdmaRPCClientEndpointGroup<Q extends RdmaRPCMessage, P extends RdmaRPCMessage> extends RdmaRPCEndpointGroup<RdmaRPCClientEndpoint<Q, P>, Q , P> {


    public RdmaRPCClientEndpointGroup(RdmaRPCProtocol<Q, P> protocol, int timeout, int maxinline, int recvQueue, int sendQueue) throws IOException {
        super(protocol, timeout, maxinline, recvQueue, sendQueue);
    }

    public RdmaCqProvider createCqProvider(RdmaRPCClientEndpoint<Q, P> endpoint) throws IOException {
        return null;
    }

    public IbvQP createQpProvider(RdmaRPCClientEndpoint<Q, P> endpoint) throws IOException {
        return null;
    }

    public void allocateResources(RdmaRPCClientEndpoint<Q, P> endpoint) throws Exception {

    }

}
