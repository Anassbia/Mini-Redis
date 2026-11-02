

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import storage.KeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class KeyValueStoreTest {

    @Test
    void testSetAndGet() {
        KeyValueStore store = new KeyValueStore();

        assertNull(store.get("missing"));

        store.set("name", "Anas");
        assertEquals("Anas", store.get("name"));

        store.set("name", "Ali");
        assertEquals("Ali", store.get("name"));
    }

    @Test
    void testDelete() {
        KeyValueStore store = new KeyValueStore();

        store.set("key", "value");

        assertTrue(store.del("key"));
        assertNull(store.get("key"));
        assertFalse(store.del("key"));
    }

    @Test
    void testSetnx() {
        KeyValueStore store = new KeyValueStore();

        assertTrue(store.setnx("name", "Anas"));
        assertFalse(store.setnx("name", "Ali"));
        assertEquals("Anas", store.get("name"));
    }

    @Test
    void testIncr() {
        KeyValueStore store = new KeyValueStore();

        assertEquals(1L, store.incr("counter"));
        assertEquals(2L, store.incr("counter"));
        assertEquals("2", store.get("counter"));

        store.set("counter", "10");
        assertEquals(11L, store.incr("counter"));
    }

    @Test
    void testDecr() {
        KeyValueStore store = new KeyValueStore();

        assertEquals(-1L, store.decr("counter"));
        assertEquals(-2L, store.decr("counter"));
        assertEquals("-2", store.get("counter"));

        store.set("counter", "10");
        assertEquals(9L, store.decr("counter"));
    }

    @Test
    void testInvalidNumber() {
        KeyValueStore store = new KeyValueStore();

        store.set("name", "hello");

        assertThrows(NumberFormatException.class,
                () -> store.incr("name"));

        assertThrows(NumberFormatException.class,
                () -> store.decr("name"));

        assertEquals("hello", store.get("name"));
    }

    @Test
    @Timeout(15)
    void testConcurrentIncrements() throws Exception {
        KeyValueStore store = new KeyValueStore();

        int threads = 8;
        int increments = 2000;

        ExecutorService pool =
                Executors.newFixedThreadPool(threads);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();

                    for (int j = 0; j < increments; j++) {
                        store.incr("counter");
                    }

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get();
            }

            assertEquals("16000", store.get("counter"));
        } finally {
            pool.shutdownNow();
        }
    }
}
