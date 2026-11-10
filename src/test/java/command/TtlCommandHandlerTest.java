package command;

import org.junit.jupiter.api.Test;
import protocol.response.Response;
import storage.KeyValueStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the EXPIRE/TTL command layer independently of TCP. */
class TtlCommandHandlerTest {

    private final KeyValueStore store = new KeyValueStore();
    private final CommnadHandler handler = new CommnadHandler(store);

    private Response execute(String... words) {
        return handler.handle(List.of(words));
    }

    private static long integer(Response response) {
        return assertInstanceOf(Response.LongValue.class, response).value();
    }

    private static void assertError(Response response) {
        Response.Error error = assertInstanceOf(Response.Error.class, response);
        assertTrue(error.message().startsWith("ERR"), "Unexpected error: " + error.message());
    }

    @Test void expireSuccessReturnsOneAndKeepsValue() {
        assertEquals(new Response.SimpleString("OK"), execute("SET", "k", "value"));
        assertEquals(1L, integer(execute("EXPIRE", "k", "60")));
        assertEquals(new Response.BulkString("value"), execute("GET", "k"));
    }

    @Test void expireMissingReturnsZero() {
        assertEquals(0L, integer(execute("EXPIRE", "missing", "60")));
    }

    @Test void ttlMissingReturnsMinusTwo() {
        assertEquals(-2L, integer(execute("TTL", "missing")));
    }

    @Test void ttlPermanentReturnsMinusOne() {
        execute("SET", "name", "Anas");
        assertEquals(-1L, integer(execute("TTL", "name")));
    }

    @Test void ttlPositiveReturnsRemainingWholeSeconds() {
        execute("SET", "k", "v");
        execute("EXPIRE", "k", "60");
        long ttl = integer(execute("TTL", "k"));
        assertTrue(ttl >= 58 && ttl <= 60, "Unexpected TTL: " + ttl);
    }

    @Test void expireZeroReturnsOneAndImmediatelyDeletesKey() {
        execute("SET", "k", "v");
        assertEquals(1L, integer(execute("EXPIRE", "k", "0")));
        assertEquals(new Response.NullValue(), execute("GET", "k"));
        assertEquals(-2L, integer(execute("TTL", "k")));
    }

    @Test void expireNegativeReturnsOneAndImmediatelyDeletesKey() {
        execute("SET", "k", "v");
        assertEquals(1L, integer(execute("EXPIRE", "k", "-2")));
        assertEquals(new Response.NullValue(), execute("GET", "k"));
    }

    @Test void lowercaseCommandsWork() {
        execute("set", "k", "v");
        assertEquals(1L, integer(execute("expire", "k", "30")));
        long ttl = integer(execute("ttl", "k"));
        assertTrue(ttl >= 28 && ttl <= 30);
    }

    @Test void expireInvalidNumberIsRedisErrorAndSessionCanContinue() {
        execute("SET", "k", "v");
        assertError(execute("EXPIRE", "k", "not-a-number"));
        assertEquals(new Response.BulkString("v"), execute("GET", "k"));
        assertEquals(-1L, integer(execute("TTL", "k")));
    }

    @Test void expireOverflowIsRedisErrorAndDoesNotChangeKey() {
        execute("SET", "k", "v");
        assertError(execute("EXPIRE", "k", Long.toString(Long.MAX_VALUE)));
        assertEquals(new Response.BulkString("v"), execute("GET", "k"));
        assertEquals(-1L, integer(execute("TTL", "k")));
    }

    @Test void expireWrongArgumentCountIsError() {
        assertError(execute("EXPIRE"));
        assertError(execute("EXPIRE", "key"));
        assertError(execute("EXPIRE", "key", "60", "extra"));
    }

    @Test void ttlWrongArgumentCountIsError() {
        assertError(execute("TTL"));
        assertError(execute("TTL", "key", "extra"));
    }

    @Test void incrementRetainsTtlThroughCommandLayer() {
        execute("SET", "counter", "10");
        execute("EXPIRE", "counter", "60");
        assertEquals(11L, integer(execute("INCR", "counter")));
        long ttl = integer(execute("TTL", "counter"));
        assertTrue(ttl >= 58 && ttl <= 60);
    }
}
