package storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Focused tests for the immutable expiration metadata object. */
class StoredValueTest {

    @Test
    void permanentFactoryStoresTheSuppliedValue() {
        StoredValue entry = StoredValue.withoutExpiration("Anas");
        assertEquals("Anas", entry.value());
        assertEquals(Long.MAX_VALUE, entry.expiresAt());
    }

    @Test
    void permanentEntryDoesNotExpire() {
        StoredValue entry = StoredValue.withoutExpiration("value");
        assertFalse(entry.isExpired(System.currentTimeMillis()));
    }

    @Test
    void entryIsValidBeforeDeadlineAndExpiredAtDeadline() {
        StoredValue entry = new StoredValue("value", 10_000L);
        assertFalse(entry.isExpired(9_999L));
        assertTrue(entry.isExpired(10_000L));
        assertTrue(entry.isExpired(10_001L));
    }

    @Test
    void recordsKeepIndependentValuesAndDeadlines() {
        StoredValue first = new StoredValue("first", 100L);
        StoredValue second = new StoredValue("second", 200L);
        assertEquals("first", first.value());
        assertEquals(100L, first.expiresAt());
        assertEquals("second", second.value());
        assertEquals(200L, second.expiresAt());
    }
}
