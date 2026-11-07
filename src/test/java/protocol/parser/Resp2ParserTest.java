package protocol.parser;

import org.junit.jupiter.api.Test;
import protocol.Parser.Resp2Parser;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Resp2ParserTest {

    private static Resp2Parser parser(String wire) {
        return new Resp2Parser(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)));
    }

    private static String command(String... args) {
        StringBuilder wire = new StringBuilder("*" + args.length + "\r\n");
        for (String arg : args) {
            wire.append('$').append(arg.getBytes(StandardCharsets.UTF_8).length)
                    .append("\r\n").append(arg).append("\r\n");
        }
        return wire.toString();
    }

    @Test
    void parsesSetCommand() throws IOException {
        assertEquals(List.of("SET", "name", "Anas"),
                parser("*3\r\n$3\r\nSET\r\n$4\r\nname\r\n$4\r\nAnas\r\n").readCommand());
    }

    @Test
    void parsesGetCommand() throws IOException {
        assertEquals(List.of("GET", "name"), parser(command("GET", "name")).readCommand());
    }

    @Test
    void preservesEmptyBulkStringArguments() throws IOException {
        assertEquals(List.of("SET", "k", ""), parser(command("SET", "k", "")).readCommand());
    }

    @Test
    void readsUnicodeUsingByteLengths() throws IOException {
        assertEquals(List.of("SET", "clé", "été"),
                parser(command("SET", "clé", "été")).readCommand());
    }

    @Test
    void preservesCrLfInsideBulkContent() throws IOException {
        assertEquals(List.of("SET", "k", "a\r\nb"),
                parser(command("SET", "k", "a\r\nb")).readCommand());
    }

    @Test
    void readsPipelinedCommandsAndThenCleanEof() throws IOException {
        Resp2Parser p = parser(command("SET", "x", "5") + command("GET", "x"));
        assertEquals(List.of("SET", "x", "5"), p.readCommand());
        assertEquals(List.of("GET", "x"), p.readCommand());
        assertNull(p.readCommand(), "EOF between commands must end the session normally");
    }

    @Test
    void returnsNullForConnectionWithNoCommands() throws IOException {
        assertNull(parser("").readCommand());
    }

    @Test
    void rejectsEmptyAndOversizedArraysBeforeAllocating() {
        assertThrows(IOException.class, () -> parser("*0\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*1025\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*2147483647\r\n").readCommand());
    }

    @Test
    void rejectsTooLargeBulkStringWithoutReadingPayload() {
        assertThrows(IOException.class, () -> parser("*1\r\n$1048577\r\n").readCommand());
    }

    @Test
    void rejectsInvalidArrayAndBulkPrefixes() {
        assertThrows(IOException.class, () -> parser("PING\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*1\r\n#3\r\nGET\r\n").readCommand());
    }

    @Test
    void rejectsInvalidOrMissingLengthDigits() {
        assertThrows(IOException.class, () -> parser("*\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*abc\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*999999999999999999\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*1\r\n$-1\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*1\r\n$\r\n").readCommand());
    }

    @Test
    void rejectsMalformedLineEndings() {
        assertThrows(IOException.class, () -> parser("*1\n$3\r\nGET\r\n").readCommand());
        assertThrows(IOException.class, () -> parser("*1\rX").readCommand());
        assertThrows(IOException.class, () -> parser("*1\r\n$3\nGET\r\n").readCommand());
    }

    @Test
    void rejectsIncompleteArrayHeaderWithEofException() {
        assertThrows(EOFException.class, () -> parser("*2\r").readCommand());
        assertThrows(EOFException.class, () -> parser("*2").readCommand());
    }

    @Test
    void rejectsEofBeforeNextRequiredBulkString() {
        assertThrows(EOFException.class, () -> parser("*2\r\n$3\r\nGET\r\n").readCommand());
    }

    @Test
    void rejectsIncompleteBulkStringContent() {
        assertThrows(EOFException.class, () -> parser("*1\r\n$5\r\nHi").readCommand());
    }

    @Test
    void rejectsMissingOrCorruptBulkTerminator() {
        assertThrows(EOFException.class, () -> parser("*1\r\n$3\r\nSET").readCommand());
        assertThrows(IOException.class, () -> parser("*1\r\n$3\r\nSETXX").readCommand());
    }
}
