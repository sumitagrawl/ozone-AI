package org.openucx.rdma;

import org.openucx.jucx.UcxCallback;
import org.openucx.jucx.ucp.*;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * Minimal UCX/JUCX-based server that accepts a single client connection and
 * performs a simple request/response (echo) exchange using tagged send/recv.
 *
 * This class has NO dependency on Spark – it uses only the JUCX APIs.
 */
public class UcxRdmaServer implements Closeable {

    private final UcpContext context;
    private final UcpWorker worker;
    private final UcpListener listener;

    private volatile UcpEndpoint endpoint;

    private final CountDownLatch connectionEstablished = new CountDownLatch(1);

    public UcxRdmaServer(String host, int port) {
        UcpParams params = new UcpParams()
                .requestTagFeature()
                .setMtWorkersShared(true);

        this.context = new UcpContext(params);
        this.worker = context.newWorker(new UcpWorkerParams());

        UcpListenerParams listenerParams = new UcpListenerParams()
                .setSockAddr(new InetSocketAddress(host, port))
                .setConnectionHandler(this::onConnection);

        this.listener = worker.newListener(listenerParams);
    }

    private void onConnection(UcpConnectionRequest request) {
        UcpEndpointParams epParams = new UcpEndpointParams()
                .setConnectionRequest(request)
                .setPeerErrorHandlingMode();

        this.endpoint = worker.newEndpoint(epParams);
        connectionEstablished.countDown();
    }

    /**
     * Blocks until a client is connected or the timeout elapses.
     */
    public void awaitConnection(long timeout) throws InterruptedException {
         connectionEstablished.await();
    }

    /**
     * Receives a single message from the client with the given maximum length,
     * and immediately sends the same bytes back as a reply.
     *
     * @return the received payload as a String using UTF-8 encoding.
     */
    public String receiveAndEcho(int maxMessageLength) throws Exception {
        if (endpoint == null) {
            throw new IllegalStateException("No client connected yet.");
        }

        ByteBuffer recvBuffer = ByteBuffer.allocateDirect(maxMessageLength);

        // Post a tagged receive on the worker (tag 0, any message length up to maxMessageLength).
        UcpRequest recvReq = worker.recvTaggedNonBlocking(
                recvBuffer,
                0L,
                ~0L,
                new UcxCallback() {});

        // Progress the worker until the receive is completed.
        while (!recvReq.isCompleted()) {
            worker.progress();
        }

        long bytesReceived = recvReq.getRecvSize();
        recvBuffer.limit((int) bytesReceived);
        recvBuffer.rewind();

        byte[] data = new byte[(int) bytesReceived];
        recvBuffer.get(data);

        // Echo the same bytes back to the client on tag 0.
        ByteBuffer sendBuffer = ByteBuffer.allocateDirect((int) bytesReceived);
        sendBuffer.put(data);
        sendBuffer.rewind();

        UcpRequest sendReq = endpoint.sendTaggedNonBlocking(
                sendBuffer,
                0L,
                new UcxCallback() {});

        while (!sendReq.isCompleted()) {
            worker.progress();
        }

        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        if (endpoint != null) {
            endpoint.close();
        }
        listener.close();
        worker.close();
        context.close();
    }
}


