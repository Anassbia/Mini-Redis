package command;

import org.junit.jupiter.api.Test;
import protocol.response.Response;
import storage.KeyValueStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandHandlerTest {

    private final KeyValueStore store = new KeyValueStore();
    private final CommnadHandler handler = new CommnadHandler(store);

    @Test
    void setAndGetReturnTypedResponses() {
        assertEquals(new Response.NullValue(), handler.handle(List.of("GET", "missing")));
        assertEquals(new Response.SimpleString("OK"), handler.handle(List.of("SET", "name", "Anas")));
        assertEquals(new Response.BulkString("Anas"), handler.handle(List.of("GET", "name")));

        // The actual string "NULL" must NOT be confused with an absent key.
        assertEquals(new Response.SimpleString("OK"), handler.handle(List.of("SET", "literal", "NULL")));
        assertEquals(new Response.BulkString("NULL"), handler.handle(List.of("GET", "literal")));
    }

    @Test
    void commandsAreCaseInsensitive() {
        assertEquals(new Response.SimpleString("OK"), handler.handle(List.of("sEt", "city", "Rabat")));
        assertEquals(new Response.BulkString("Rabat"), handler.handle(List.of("gEt", "city")));
    }

    @Test
    void incrAndDecrReturnNumbersAndUpdateStorage() {
        assertEquals(new Response.LongValue(1), handler.handle(List.of("INCR", "counter")));
        assertEquals(new Response.LongValue(2), handler.handle(List.of("INCR", "counter")));
        assertEquals(new Response.LongValue(1), handler.handle(List.of("DECR", "counter")));
        assertEquals("1", store.get("counter"));
        assertEquals(new Response.LongValue(-1), handler.handle(List.of("DECR", "fresh")));
    }

    @Test
    void delAndSetnxReturnNumericResponses() {
        assertEquals(new Response.LongValue(1), handler.handle(List.of("SETNX", "name", "Anas")));
        assertEquals(new Response.LongValue(0), handler.handle(List.of("SETNX", "name", "Ali")));
        assertEquals(new Response.BulkString("Anas"), handler.handle(List.of("GET", "name")));
        assertEquals(new Response.LongValue(1), handler.handle(List.of("DEL", "name")));
        assertEquals(new Response.LongValue(0), handler.handle(List.of("DEL", "name")));
        assertEquals(new Response.NullValue(), handler.handle(List.of("GET", "name")));
    }

    @Test
    void wrongArgumentCountsAreRejectedForEveryCommand() {
        Response error = new Response.Error("ERR wrong number of arguments");
        assertEquals(error, handler.handle(List.of("SET", "key")));
        assertEquals(error, handler.handle(List.of("GET")));
        assertEquals(error, handler.handle(List.of("DEL", "key", "extra")));
        assertEquals(error, handler.handle(List.of("INCR")));
        assertEquals(error, handler.handle(List.of("DECR", "key", "extra")));
        assertEquals(error, handler.handle(List.of("SETNX", "key")));
    }

    @Test
    void invalidNumbersReturnAnErrorWithoutChangingTheStoredValue() {
        store.set("name", "hello");
        Response error = new Response.Error("ERR value is not an integer");
        assertEquals(error, handler.handle(List.of("INCR", "name")));
        assertEquals(error, handler.handle(List.of("DECR", "name")));
        assertEquals("hello", store.get("name"));
    }

    @Test
    void emptyAndUnknownCommandsAreRejected() {
        assertEquals(new Response.Error("ERR empty command"), handler.handle(List.of()));
        assertEquals(new Response.Error("ERR empty command"), handler.handle(null));
        assertEquals(new Response.Error("ERR unknown command"), handler.handle(List.of("HELLO")));
    }
}
