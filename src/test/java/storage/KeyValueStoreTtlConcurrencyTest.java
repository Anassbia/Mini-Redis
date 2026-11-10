package storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Race-focused tests: operations must be atomic for a single key. */
class KeyValueStoreTtlConcurrencyTest {

    @Test @Timeout(12)
    void concurrentSetnxHasExactlyOneWinner() throws Exception {
        KeyValueStore store = new KeyValueStore();
        int workers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                final int id = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return store.setnx("same", "candidate-" + id);
                }));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(5, TimeUnit.SECONDS)) winners++;
            }
            assertEquals(1, winners);
            assertTrue(store.get("same").startsWith("candidate-"));
            assertEquals(-1L, store.ttl("same"));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test @Timeout(12)
    void concurrentDeleteHasExactlyOneWinner() throws Exception {
        KeyValueStore store = new KeyValueStore();
        store.set("same", "value");
        int workers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return store.del("same");
                }));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(5, TimeUnit.SECONDS)) winners++;
            }
            assertEquals(1, winners);
            assertNull(store.get("same"));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test @Timeout(20)
    void simultaneousIncrementsPreserveCountAndExpirationDeadline() throws Exception {
        KeyValueStore store = new KeyValueStore();
        store.set("counter", "0");
        assertTrue(store.expire("counter", 60));
        int workers = 8;
        int incrementsPerWorker = 500;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int j = 0; j < incrementsPerWorker; j++) store.incr("counter");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS);
            assertEquals(Long.toString((long) workers * incrementsPerWorker), store.get("counter"));
            long ttl = store.ttl("counter");
            assertTrue(ttl >= 1 && ttl <= 60, "Counter lost its TTL: " + ttl);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
