package tech.shunzi.rpc.rdma.service;

import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;
import tech.shunzi.rpc.rdma.endpoint.server.RdmaRPCServerEndpoint;

public class RdmaRPCServerEvent<Q extends RdmaRPCMessage, P extends RdmaRPCMessage> {
    private RdmaRPCServerEndpoint<Q,P> endpoint;
    private Q request;
    private P response;
    private int ticket;

    public RdmaRPCServerEvent(RdmaRPCServerEndpoint<Q, P> endpoint, Q request, P response) {
        this.endpoint = endpoint;
        this.request = request;
        this.response = response;
    }

    /**
     * For server endpoint, request is the received message.
     * @return request
     */
    public Q getReceiveMessage() {
        return request;
    }

    /**
     * For server endpoint, response is the sent message.
     * @return response
     */
    public P getSendMessage() {
        return response;
    }

    public int getTicket() {
        return ticket;
    }

    public void setTicket(int ticket) {
        this.ticket = ticket;
    }
}
