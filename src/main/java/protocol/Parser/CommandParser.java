package protocol.Parser;

import java.io.IOException;
import java.util.List;

public interface CommandParser {
    List<String> readCommand() throws IOException;
}
