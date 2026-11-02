package protocol.serializer;

import org.junit.jupiter.api.Test;
import protocol.response.Response;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSerializerTest {

    @Test
    void serializesAllResponseTypesWithOneLineEach() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ResponseSerializer serializer = new TextSerializer(output);

        serializer.serialize(new Response.SimpleString("OK"));
        serializer.serialize(new Response.BulkString("Anas"));
        serializer.serialize(new Response.LongValue(42));
        serializer.serialize(new Response.LongValue(-1));
        serializer.serialize(new Response.Error("ERR wrong number of arguments"));
        serializer.serialize(new Response.NullValue());

        String newline = System.lineSeparator();
        assertEquals(
                "OK" + newline +
                "Anas" + newline +
                "42" + newline +
                "-1" + newline +
                "ERR wrong number of arguments" + newline +
                "NULL" + newline,
                output.toString(StandardCharsets.UTF_8)
        );
    }

    @Test
    void flushesEveryResponseAndPreservesUtf8Text() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ResponseSerializer serializer = new TextSerializer(output);

        serializer.serialize(new Response.BulkString("café"));
        // A buffered writer that forgot flush() would leave this empty here.
        assertEquals("café" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
    }
}
