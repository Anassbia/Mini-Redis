package server;

import command.CommnadHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import storage.KeyValueStore;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Deterministic capacity test: 4 workers + 8 queued tasks + 1 rejected. */
class ClientConnectionManagerSaturationTest {

    @Test
    @Timeout(15)
    void thirteenClientsSaturateFourWorkersAndEightQueueSlots() throws Exception {
        CountDownLatch allWorkersBusy = new CountDownLatch(4);
        CountDownLatch releaseWorkers = new CountDownLatch(1);

        // An intentionally blocking session, not a real TCP parser. This isolates
        // the queue/rejection behavior from network timing and port availability.
        ClientSessionHandler blockingSession = new ClientSessionHandler(
                new CommnadHandler(new KeyValueStore())) {
            @Override
            public void handle(Socket socket) throws IOException {
                allWorkersBusy.countDown();
                try {
                    releaseWorkers.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while holding worker", e);
                }
            }
        };

        ClientConnectionManager manager = new ClientConnectionManager(blockingSession);
        List<Socket> submittedSockets = new ArrayList<>();
        try {
            // Start exactly 4 blocked worker tasks.
            for (int i = 0; i < 4; i++) {
                Socket socket = new Socket();
                submittedSockets.add(socket);
                manager.submit(socket);
            }
            assertTrue(allWorkersBusy.await(3, TimeUnit.SECONDS),
                    "The four worker threads never started");

            // Fill all 8 queue slots while workers cannot finish.
            for (int i = 0; i < 8; i++) {
                Socket socket = new Socket();
                submittedSockets.add(socket);
                manager.submit(socket);
                assertFalse(socket.isClosed(), "Queued client should not be rejected");
            }

            // The 13th task has nowhere to go and must be rejected and closed.
            Socket rejectedSocket = new Socket();
            submittedSockets.add(rejectedSocket);
            manager.submit(rejectedSocket);
            assertTrue(rejectedSocket.isClosed(),
                    "Rejected client's socket should be closed immediately");

        } finally {
            releaseWorkers.countDown();
            manager.shutdown();
            for (Socket socket : submittedSockets) {
                socket.close();
            }
        }
    }
}
