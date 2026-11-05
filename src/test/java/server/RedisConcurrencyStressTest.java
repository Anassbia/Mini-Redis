package server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Black-box tests of simultaneous commands and short-lived TCP connections. */
class RedisConcurrencyStressTest {

    @Test
    @Timeout(35)
    void eightClientsIncrementOneKeyWithoutLostUpdates() throws Exception {
        final int clients = 8;
        final int incrementsPerClient = 250;
        String key = "m4_counter_" + UUID.randomUUID().toString().replace("-", "");

        try (RedisStressSupport.RunningServer running = RedisStressSupport.startServer()) {
            try (RedisStressSupport.Client setup = RedisStressSupport.connect()) {
                assertEquals("OK", setup.command("SET " + key + " 0"));
            }

            ExecutorService callers = Executors.newFixedThreadPool(clients);
            CountDownLatch ready = new CountDownLatch(clients);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < clients; i++) {
                    futures.add(callers.submit(() -> {
                        try (RedisStressSupport.Client client = RedisStressSupport.connect()) {
                            ready.countDown();
                            assertTrue(go.await(8, TimeUnit.SECONDS), "Start gate timed out");
                            for (int n = 0; n < incrementsPerClient; n++) {
                                String response = client.command("INCR " + key);
                                assertNotNull(response, "Connection closed during INCR");
                                assertTrue(response.matches("[0-9]+"),
                                        "INCR did not return a numeric value: " + response);
                            }
                            return null;
                        }
                    }));
                }
                assertTrue(ready.await(8, TimeUnit.SECONDS), "Clients did not connect");
                go.countDown();
                for (Future<?> f : futures) {
                    f.get(25, TimeUnit.SECONDS); // Rethrows assertion failures from clients.
                }

                try (RedisStressSupport.Client verify = RedisStressSupport.connect()) {
                    assertEquals(Integer.toString(clients * incrementsPerClient),
                            verify.command("GET " + key), "Concurrent INCR lost updates");
                }
            } finally {
                go.countDown();
                callers.shutdownNow();
                callers.awaitTermination(3, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @Timeout(35)
    void repeatedConnectCommandDisconnectCyclesRemainResponsive() throws Exception {
        final int parallelClients = 4; // At most 4 workers occupied; do not test overload here.
        final int connectionsPerClient = 30;
        String prefix = "m4_churn_" + UUID.randomUUID().toString().replace("-", "");

        try (RedisStressSupport.RunningServer running = RedisStressSupport.startServer()) {
            ExecutorService callers = Executors.newFixedThreadPool(parallelClients);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int c = 0; c < parallelClients; c++) {
                    final int clientId = c;
                    futures.add(callers.submit(() -> {
                        for (int n = 0; n < connectionsPerClient; n++) {
                            String key = prefix + "_" + clientId + "_" + n;
                            try (RedisStressSupport.Client client = RedisStressSupport.connect()) {
                                assertEquals("OK", client.command("SET " + key + " value"));
                                assertEquals("value", client.command("GET " + key));
                                assertEquals("1", client.command("DEL " + key));
                            }
                        }
                        return null;
                    }));
                }

                for (Future<?> future : futures) {
                    future.get(25, TimeUnit.SECONDS);
                }
                try (RedisStressSupport.Client check = RedisStressSupport.connect()) {
                    assertEquals("NULL", check.command("GET " + prefix + "_0_0"));
                }
            } finally {
                callers.shutdownNow();
                callers.awaitTermination(3, TimeUnit.SECONDS);
            }
        }
    }
}
