package server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Stops a live server while sessions exist and while many threads request shutdown. */
class RedisShutdownStressTest {

    @Test
    @Timeout(20)
    void eightConcurrentStopRequestsDoNotInterfere() throws Exception {
        try (RedisStressSupport.RunningServer running = RedisStressSupport.startServer();
             RedisStressSupport.Client idle = RedisStressSupport.connect()) {
            assertEquals("NULL", idle.command("GET m4_nonexistent"));

            int callers = 8;
            CountDownLatch ready = new CountDownLatch(callers);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService threads = Executors.newFixedThreadPool(callers);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < callers; i++) {
                    futures.add(threads.submit(() -> {
                        ready.countDown();
                        assertTrue(go.await(5, TimeUnit.SECONDS));
                        running.server.stop();
                        return null;
                    }));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                go.countDown();
                for (Future<?> future : futures) {
                    future.get(8, TimeUnit.SECONDS);
                }
                idle.assertDisconnected();
            } finally {
                go.countDown();
                threads.shutdownNow();
                threads.awaitTermination(3, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @Timeout(25)
    void shutdownWhileFourClientsAreSendingCommands() throws Exception {
        try (RedisStressSupport.RunningServer running = RedisStressSupport.startServer()) {
            List<RedisStressSupport.Client> clients = new ArrayList<>();
            ExecutorService senders = Executors.newFixedThreadPool(4);
            CountDownLatch allClientsActive = new CountDownLatch(4);
            AtomicBoolean shutdownRequested = new AtomicBoolean(false);
            List<Future<Boolean>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < 4; i++) {
                    RedisStressSupport.Client client = RedisStressSupport.connect();
                    clients.add(client);
                    assertEquals("NULL", client.command("GET m4_active_probe"));
                }

                // Four separate live sessions continuously exchange commands.
                for (int i = 0; i < 4; i++) {
                    RedisStressSupport.Client client = clients.get(i);
                    futures.add(senders.submit(() -> {
                        int received = 0;
                        while (true) {
                            try {
                                String reply = client.command("INCR m4_shutdown_counter");
                                if (reply == null) {
                                    assertTrue(shutdownRequested.get(),
                                            "Unexpected EOF before requesting shutdown");
                                    return true;
                                }
                                assertTrue(reply.matches("[0-9]+"),
                                        "Unexpected INCR reply: " + reply);
                                if (++received == 10) {
                                    allClientsActive.countDown();
                                }
                            } catch (java.net.SocketTimeoutException e) {
                                throw e; // A timeout is not evidence of a clean shutdown.
                            } catch (java.io.IOException e) {
                                if (shutdownRequested.get()) {
                                    return true; // Peer socket closed during shutdown.
                                }
                                throw e; // Unexpected failure before shutdown.
                            }
                        }
                    }));
                }

                assertTrue(allClientsActive.await(8, TimeUnit.SECONDS),
                        "Not all clients executed ten commands");
                shutdownRequested.set(true);
                running.server.stop();

                for (Future<Boolean> f : futures) {
                    assertTrue(f.get(8, TimeUnit.SECONDS),
                            "Client did not observe shutdown");
                }
            } finally {
                shutdownRequested.set(true);
                running.server.stop();
                for (RedisStressSupport.Client client : clients) {
                    client.close();
                }
                senders.shutdownNow();
                senders.awaitTermination(3, TimeUnit.SECONDS);
            }
        }
    }
}
