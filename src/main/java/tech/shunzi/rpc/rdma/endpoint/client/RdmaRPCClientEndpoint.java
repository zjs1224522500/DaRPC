package tech.shunzi.rpc.rdma.endpoint.client;

import com.ibm.disni.verbs.RdmaCmId;
import tech.shunzi.rpc.rdma.common.RdmaRPCMessage;
import tech.shunzi.rpc.rdma.endpoint.RdmaRPCEndpoint;

import java.io.IOException;

public class RdmaRPCClientEndpoint<Q extends RdmaRPCMessage, P extends RdmaRPCMessage> extends RdmaRPCEndpoint<Q, P> {
    protected RdmaRPCClientEndpoint(RdmaRPCClientEndpointGroup<Q, P> group, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(group, idPriv, serverSide);
    }
}
