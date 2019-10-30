package tech.shunzi.rpc.rdma.service;

import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;
import tech.shunzi.rpc.rdma.common.RdmaRPCProtocol;
import tech.shunzi.rpc.rdma.endpoint.server.RdmaRPCServerEndpoint;

public interface RdmaRPCServerService<Q extends RdmaRPCMessage, P extends RdmaRPCMessage> extends RdmaRPCProtocol<Q, P> {
    void processServerEvent(RdmaRPCServerEvent<Q, P> event);
    void open(RdmaRPCServerEndpoint<Q, P> endpoint);
    void close(RdmaRPCServerEndpoint<Q, P> endpoint);
}
