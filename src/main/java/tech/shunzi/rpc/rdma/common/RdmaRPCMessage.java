package tech.shunzi.rpc.rdma.common;

import java.nio.ByteBuffer;

public interface RdmaRPCMessage {
    int write(ByteBuffer buffer);
    void update(ByteBuffer buffer);
    int size();
}
