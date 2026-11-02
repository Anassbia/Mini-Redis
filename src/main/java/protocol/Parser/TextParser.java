package protocol.Parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class TextParser implements CommandParser {
    private final BufferedReader reader;

    public TextParser(InputStream in) {
        InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
        reader = new BufferedReader(isr);
    }

    @Override
    public List<String> readCommand() throws IOException {
        String CommandLine = reader.readLine();
        if(CommandLine==null){
            return null;
        }
        CommandLine = CommandLine.trim();
        if(CommandLine.isBlank()){
            return List.of();//returns immutable list , no argummnets = empty
        }
        String[] Pieces = CommandLine.split("\\s+");
        return Arrays.asList(Pieces);//returns fixed array size
    }
}
