package storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/** Storage-level TTL semantics, including lazy expiration and integer boundaries. */
class KeyValueStoreTtlTest {

    private final KeyValueStore store = new KeyValueStore();

    private static void assertTtlInRange(long actual, long lower, long upper) {
        assertTrue(actual >= lower && actual <= upper,
                "TTL expected in [" + lower + ", " + upper + "] but was " + actual);
    }

    private static void awaitExpiry() throws InterruptedException {
        // Not a performance test. A little margin avoids checking on the one-second boundary.
        Thread.sleep(1_200);
    }

    @Test void missingKeyHasTtlMinusTwo() {
        assertEquals(-2L, store.ttl("missing"));
    }

    @Test void permanentKeyHasTtlMinusOne() {
        store.set("name", "Anas");
        assertEquals(-1L, store.ttl("name"));
    }

    @Test void expireMissingKeyReturnsFalse() {
        assertFalse(store.expire("missing", 60));
        assertEquals(-2L, store.ttl("missing"));
    }

    @Test void expireSetsDeadlineWithoutChangingValue() {
        store.set("name", "Anas");
        assertTrue(store.expire("name", 60));
        assertEquals("Anas", store.get("name"));
        assertTtlInRange(store.ttl("name"), 58, 60);
    }

    @Test void expireCanRefreshAnExistingDeadline() {
        store.set("k", "v");
        assertTrue(store.expire("k", 10));
        assertTrue(store.expire("k", 120));
        assertTtlInRange(store.ttl("k"), 118, 120);
    }

    @Test void setClearsPreviousExpiration() {
        store.set("key", "old");
        assertTrue(store.expire("key", 30));
        store.set("key", "new");
        assertEquals("new", store.get("key"));
        assertEquals(-1L, store.ttl("key"));
    }

    @Test void zeroSecondExpireDeletesExistingKeyImmediately() {
        store.set("k", "v");
        assertTrue(store.expire("k", 0));
        assertNull(store.get("k"));
        assertEquals(-2L, store.ttl("k"));
        assertFalse(store.expire("k", 0));
    }

    @Test void negativeSecondExpireDeletesExistingKeyImmediately() {
        store.set("k", "v");
        assertTrue(store.expire("k", -5));
        assertNull(store.get("k"));
        assertFalse(store.del("k"));
    }

    @Test @Timeout(8)
    void getLazilyExpiresKeyAfterDeadline() throws Exception {
        store.set("code", "123");
        assertTrue(store.expire("code", 1));
        awaitExpiry();
        assertNull(store.get("code"));
        assertEquals(-2L, store.ttl("code"));
    }

    @Test @Timeout(8)
    void ttlSeesElapsedExpirationEvenWithoutGet() throws Exception {
        store.set("code", "123");
        assertTrue(store.expire("code", 1));
        awaitExpiry();
        assertEquals(-2L, store.ttl("code"));
        assertNull(store.get("code"));
    }

    @Test void setnxDoesNotOverwriteValidKeyOrChangeItsTtl() {
        store.set("k", "old");
        assertTrue(store.expire("k", 60));
        assertFalse(store.setnx("k", "new"));
        assertEquals("old", store.get("k"));
        assertTtlInRange(store.ttl("k"), 58, 60);
    }

    @Test void setnxInsertsMissingKeyAsPermanent() {
        assertTrue(store.setnx("k", "new"));
        assertEquals("new", store.get("k"));
        assertEquals(-1L, store.ttl("k"));
    }

    @Test @Timeout(8)
    void setnxReplacesLogicallyExpiredKey() throws Exception {
        store.set("k", "old");
        assertTrue(store.expire("k", 1));
        awaitExpiry();
        assertTrue(store.setnx("k", "new"));
        assertEquals("new", store.get("k"));
        assertEquals(-1L, store.ttl("k"));
    }

    @Test void delReturnsTrueOnlyForValidExistingKey() {
        store.set("k", "v");
        assertTrue(store.del("k"));
        assertFalse(store.del("k"));
        assertEquals(-2L, store.ttl("k"));
    }

    @Test @Timeout(8)
    void delReturnsFalseForLogicallyExpiredKey() throws Exception {
        store.set("k", "v");
        assertTrue(store.expire("k", 1));
        awaitExpiry();
        assertFalse(store.del("k"));
        assertNull(store.get("k"));
    }

    @Test void incrementCreatesPermanentKeyWhenMissing() {
        assertEquals(1L, store.incr("counter"));
        assertEquals("1", store.get("counter"));
        assertEquals(-1L, store.ttl("counter"));
    }

    @Test void decrementCreatesPermanentKeyWhenMissing() {
        assertEquals(-1L, store.decr("counter"));
        assertEquals("-1", store.get("counter"));
        assertEquals(-1L, store.ttl("counter"));
    }

    @Test void incrementAndDecrementPreserveExistingTtl() {
        store.set("counter", "10");
        assertTrue(store.expire("counter", 60));
        assertEquals(11L, store.incr("counter"));
        assertEquals(10L, store.decr("counter"));
        assertEquals("10", store.get("counter"));
        assertTtlInRange(store.ttl("counter"), 58, 60);
    }

    @Test @Timeout(8)
    void incrementTreatsExpiredNumericEntryAsMissing() throws Exception {
        store.set("counter", "100");
        assertTrue(store.expire("counter", 1));
        awaitExpiry();
        assertEquals(1L, store.incr("counter"));
        assertEquals(-1L, store.ttl("counter"));
    }

    @Test @Timeout(8)
    void decrementTreatsExpiredNumericEntryAsMissing() throws Exception {
        store.set("counter", "100");
        assertTrue(store.expire("counter", 1));
        awaitExpiry();
        assertEquals(-1L, store.decr("counter"));
        assertEquals(-1L, store.ttl("counter"));
    }

    @Test void invalidNumericValueDoesNotLoseValueOrTtl() {
        store.set("counter", "not-a-number");
        assertTrue(store.expire("counter", 60));
        assertThrows(NumberFormatException.class, () -> store.incr("counter"));
        assertThrows(NumberFormatException.class, () -> store.decr("counter"));
        assertEquals("not-a-number", store.get("counter"));
        assertTtlInRange(store.ttl("counter"), 58, 60);
    }

    @Test void incrementOverflowDoesNotChangeStoredValue() {
        store.set("counter", Long.toString(Long.MAX_VALUE));
        assertTrue(store.expire("counter", 60));
        assertThrows(ArithmeticException.class, () -> store.incr("counter"));
        assertEquals(Long.toString(Long.MAX_VALUE), store.get("counter"));
        assertTtlInRange(store.ttl("counter"), 58, 60);
    }

    @Test void decrementUnderflowDoesNotChangeStoredValue() {
        store.set("counter", Long.toString(Long.MIN_VALUE));
        assertTrue(store.expire("counter", 60));
        assertThrows(ArithmeticException.class, () -> store.decr("counter"));
        assertEquals(Long.toString(Long.MIN_VALUE), store.get("counter"));
        assertTtlInRange(store.ttl("counter"), 58, 60);
    }

    @Test void hugeExpireDurationCannotOverflowSilentlyOrModifyKey() {
        store.set("key", "value");
        assertThrows(ArithmeticException.class, () -> store.expire("key", Long.MAX_VALUE));
        assertEquals("value", store.get("key"));
        assertEquals(-1L, store.ttl("key"));
    }
}
