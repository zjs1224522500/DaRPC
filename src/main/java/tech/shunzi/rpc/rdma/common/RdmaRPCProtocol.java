package tech.shunzi.rpc.rdma.common;

public interface RdmaRPCProtocol<Q extends  RdmaRPCMessage, P extends RdmaRPCMessage> {
    Q createRequest();
    P createResponse();
}
