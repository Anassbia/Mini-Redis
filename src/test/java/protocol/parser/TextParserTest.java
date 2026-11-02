package protocol.parser;

import org.junit.jupiter.api.Test;
import protocol.Parser.TextParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextParserTest {

    private TextParser parserFor(String input) {
        return new TextParser(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void splitsWhitespaceAndReadsSeveralCommands() throws Exception {
        TextParser parser = parserFor("  SET   name   Anas  \r\nGET\tname\n");
        assertEquals(List.of("SET", "name", "Anas"), parser.readCommand());
        assertEquals(List.of("GET", "name"), parser.readCommand());
        assertNull(parser.readCommand());
    }

    @Test
    void blankLinesAreEmptyListsButEndOfStreamIsNull() throws Exception {
        TextParser parser = parserFor("\n   \t   \nGET missing\n");
        assertEquals(List.of(), parser.readCommand());
        assertEquals(List.of(), parser.readCommand());
        assertEquals(List.of("GET", "missing"), parser.readCommand());
        assertNull(parser.readCommand());
    }

    @Test
    void readsLastLineWithoutTrailingNewline() throws Exception {
        TextParser parser = parserFor("INCR counter");
        assertEquals(List.of("INCR", "counter"), parser.readCommand());
        assertNull(parser.readCommand());
    }

    @Test
    void decodesUtf8() throws Exception {
        TextParser parser = parserFor("SET greeting café\n");
        assertEquals(List.of("SET", "greeting", "café"), parser.readCommand());
    }
}
