package tech.shunzi.rpc.rdma.endpoint.server;

import com.ibm.disni.verbs.RdmaCmEvent;
import com.ibm.disni.verbs.RdmaCmId;
import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;
import tech.shunzi.rpc.rdma.endpoint.RdmaRPCEndpoint;
import tech.shunzi.rpc.rdma.service.RdmaRPCServerEvent;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

public class RdmaRPCServerEndpoint<Q extends RdmaRPCMessage, P extends RdmaRPCMessage> extends RdmaRPCEndpoint<Q, P> {

    private RdmaRPCServerEndpointGroup<Q, P> group;
    private ArrayBlockingQueue<RdmaRPCServerEvent<Q,P>> eventPool;
    private ArrayBlockingQueue<RdmaRPCServerEvent<Q,P>> lazyEvents;
    private int getClusterId;

    protected RdmaRPCServerEndpoint(RdmaRPCServerEndpointGroup<Q, P> group, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(group, idPriv, serverSide);
        this.group = group;
        this.getClusterId = group.newClusterId();
        this.eventPool = new ArrayBlockingQueue<RdmaRPCServerEvent<Q, P>>(group.getRecvQueueSize());
        this.lazyEvents = new ArrayBlockingQueue<RdmaRPCServerEvent<Q, P>>(group.getRecvQueueSize());
    }

    /**
     * Init the event pool with request and response buffer created by group.
     * @throws IOException
     */
    public void init() throws IOException {
        super.init();
        for(int i = 0; i < group.getRecvQueueSize(); i++) {
            RdmaRPCServerEvent<Q, P> event = new RdmaRPCServerEvent<Q, P>(this, group.createRequest(), group.createResponse());
            this.eventPool.add(event);
        }
    }

    /**
     * Execute the specific SVC and send response.
     * @param event
     * @throws IOException
     */
    public void sendResponse(RdmaRPCServerEvent<Q, P> event) throws IOException {
        if (sendMessage(event.getSendMessage(), event.getTicket())){
            // Send success
            eventPool.add(event);
        } else {
            lazyEvents.add(event);
        }
    }

    public synchronized void dispatchCmEvent(RdmaCmEvent cmEvent) throws IOException {
        super.dispatchCmEvent(cmEvent);

    }

}
