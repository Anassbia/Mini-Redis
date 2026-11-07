package protocol.serializer;

import org.junit.jupiter.api.Test;
import protocol.response.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Resp2SerializerTest {

    private static byte[] encode(Response... responses) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Resp2Serializer serializer = new Resp2Serializer(out);
        for (Response response : responses) {
            serializer.serialize(response);
        }
        return out.toByteArray();
    }

    private static void assertWire(String expected, Response... responses) throws IOException {
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), encode(responses));
    }

    @Test
    void serializesSimpleStringsUsingTheirActualValues() throws IOException {
        assertWire("+OK\r\n", new Response.SimpleString("OK"));
        assertWire("+PONG\r\n", new Response.SimpleString("PONG"));
    }

    @Test
    void serializesPositiveNegativeAndLargeIntegers() throws IOException {
        assertWire(":42\r\n", new Response.LongValue(42));
        assertWire(":-7\r\n", new Response.LongValue(-7));
        assertWire(":" + Long.MAX_VALUE + "\r\n", new Response.LongValue(Long.MAX_VALUE));
    }

    @Test
    void serializesErrorResponses() throws IOException {
        assertWire("-ERR unknown command\r\n", new Response.Error("ERR unknown command"));
    }

    @Test
    void serializesNullAsNullBulkString() throws IOException {
        assertWire("$-1\r\n", new Response.NullValue());
    }

    @Test
    void serializesAsciiAndEmptyBulkStrings() throws IOException {
        assertWire("$4\r\nAnas\r\n", new Response.BulkString("Anas"));
        assertWire("$0\r\n\r\n", new Response.BulkString(""));
    }

    @Test
    void usesUtf8ByteLengthRatherThanJavaStringLength() throws IOException {
        assertWire("$2\r\né\r\n", new Response.BulkString("é"));
        assertWire("$6\r\n你好\r\n", new Response.BulkString("你好"));
    }

    @Test
    void preservesCrLfInsideBulkStrings() throws IOException {
        assertWire("$4\r\na\r\nb\r\n", new Response.BulkString("a\r\nb"));
    }

    @Test
    void writesMultipleResponsesSequentiallyWithoutExtraBytes() throws IOException {
        assertWire("+OK\r\n:1\r\n$-1\r\n",
                new Response.SimpleString("OK"),
                new Response.LongValue(1),
                new Response.NullValue());
    }

    @Test
    void flushesOncePerCompletedResponse() throws IOException {
        class TrackingStream extends ByteArrayOutputStream {
            int flushes;
            @Override public void flush() throws IOException { flushes++; }
        }
        TrackingStream out = new TrackingStream();
        Resp2Serializer serializer = new Resp2Serializer(out);
        serializer.serialize(new Response.SimpleString("OK"));
        serializer.serialize(new Response.LongValue(2));
        assertEquals(2, out.flushes);
    }
}
