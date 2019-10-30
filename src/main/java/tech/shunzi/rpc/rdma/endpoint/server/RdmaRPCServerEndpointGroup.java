package tech.shunzi.rpc.rdma.endpoint.server;

import com.ibm.disni.RdmaCqProvider;
import com.ibm.disni.verbs.IbvQP;
import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;
import tech.shunzi.rpc.rdma.common.RdmaRPCProtocol;
import tech.shunzi.rpc.rdma.endpoint.RdmaRPCEndpointGroup;
import tech.shunzi.rpc.rdma.service.RdmaRPCServerService;

import java.io.IOException;

public class RdmaRPCServerEndpointGroup<Q extends RdmaRPCMessage, P extends RdmaRPCMessage> extends RdmaRPCEndpointGroup<RdmaRPCServerEndpoint<Q, P>, Q , P> {

    private int currentCluster;
    private int numberOfClusters;
    private RdmaRPCServerService<Q, P> rpcServerService;

    public RdmaRPCServerEndpointGroup(RdmaRPCProtocol<Q, P> protocol, int timeout, int maxinline, int recvQueue, int sendQueue) throws IOException {
        super(protocol, timeout, maxinline, recvQueue, sendQueue);
    }


    public RdmaCqProvider createCqProvider(RdmaRPCServerEndpoint<Q, P> endpoint) throws IOException {
        return null;
    }

    public IbvQP createQpProvider(RdmaRPCServerEndpoint<Q, P> endpoint) throws IOException {
        return null;
    }

    public void allocateResources(RdmaRPCServerEndpoint<Q, P> endpoint) throws Exception {

    }


    synchronized int newClusterId() {
        int newClusterId = currentCluster;
        currentCluster = (currentCluster + 1) % numberOfClusters;
        return newClusterId;
    }

    public Q createRequest() {
        return rpcServerService.createRequest();
    }

    public P createResponse() {
        return rpcServerService.createResponse();
    }
}
