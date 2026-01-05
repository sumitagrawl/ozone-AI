package org.openucx.rdma;

import org.openucx.jucx.UcxCallback;
import org.openucx.jucx.ucp.*;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Minimal UCX/JUCX-based client that connects to a {@link UcxRdmaServer},
 * sends a single message and waits for a reply using tagged operations.
 *
 * This class has NO dependency on Spark – it uses only the JUCX APIs.
 */
public class UcxRdmaClient implements Closeable {

    private final UcpContext context;
    private final UcpWorker worker;
    private final UcpEndpoint endpoint;

    public UcxRdmaClient(String host, int port) {
        UcpParams params = new UcpParams()
                .requestTagFeature()
                .setMtWorkersShared(true);

        this.context = new UcpContext(params);
        this.worker = context.newWorker(new UcpWorkerParams());

        UcpEndpointParams epParams = new UcpEndpointParams()
                .setSocketAddress(new InetSocketAddress(host, port))
                .setPeerErrorHandlingMode();

        this.endpoint = worker.newEndpoint(epParams);
    }

    /**
     * Sends the given message to the server and waits for a reply of up to maxReplyLength bytes.
     *
     * @param message        message to send (UTF-8 encoded on the wire)
     * @param maxReplyLength maximum expected reply length in bytes
     * @return reply as string
     */
    public String sendAndReceive(String message, int maxReplyLength) throws Exception {
        byte[] payload = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Prepare send buffer.
        ByteBuffer sendBuffer = ByteBuffer.allocateDirect(payload.length);
        sendBuffer.put(payload);
        sendBuffer.rewind();

        // Post tagged send on tag 0.
        UcpRequest sendReq = endpoint.sendTaggedNonBlocking(
                sendBuffer,
                0L,
                new UcxCallback() {});

        // Prepare receive buffer for reply.
        ByteBuffer recvBuffer = ByteBuffer.allocateDirect(maxReplyLength);
        UcpRequest recvReq = worker.recvTaggedNonBlocking(
                recvBuffer,
                0L,
                ~0L,
                new UcxCallback() {});

        // Progress until both operations are completed.
        while (!sendReq.isCompleted() || !recvReq.isCompleted()) {
            worker.progress();
        }

        long bytesReceived = recvReq.getRecvSize();
        recvBuffer.limit((int) bytesReceived);
        recvBuffer.rewind();

        byte[] replyBytes = new byte[(int) bytesReceived];
        recvBuffer.get(replyBytes);

        return new String(replyBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        endpoint.close();
        worker.close();
        context.close();
    }
}


