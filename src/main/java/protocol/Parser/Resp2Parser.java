package protocol.Parser;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Resp2Parser implements CommandParser{
    private final InputStream in;
    private static final int MAX_BULK_LENGTH = 1024 * 1024;// 1mib

    public Resp2Parser(InputStream in){
        this.in=in;
    }

    @Override
    public List<String> readCommand() throws IOException {
        int argNum = readArrayLength();
        if(argNum==-1){
            return null;
        }
        if(argNum == 0 ||  argNum > 1024){
            throw new IOException("arguments number out of range");
        }
        String[]  args = new String[argNum];
        for(int i=0;i<argNum;i++){
            args[i] = readBulkString();
        }

        return List.of(args);
    }

    private int  readArrayLength() throws IOException {
      return readLengthHeader('*',true);
    }

    private String readBulkString() throws IOException {
        int length =  readLengthHeader('$',false);
            if(length > MAX_BULK_LENGTH){
                throw new IOException("Bulk String Too Large");
            }
        byte[] content = in.readNBytes(length);

            if (content.length != length) {
                throw new EOFException("incomplete bulk string content");
            }
            int cr = in.read();
            int lf = in.read();

            if (cr == -1 || lf == -1) {
                throw new EOFException("Missing bulk string terminator");
            }

            if (cr != '\r' || lf != '\n') {
                throw new IOException("Invalid bulk string terminator");
            }
        return new String(content, StandardCharsets.UTF_8);
    }

    private int readLengthHeader(char prefix,boolean allowCleanEOF) throws IOException{
        int b = in.read();
        if (b == -1) {
            if (allowCleanEOF) {
                return -1;
            }
            throw new EOFException("Incomplete RESP2 command");
        }

            StringBuilder s =  new StringBuilder();
        if (b != prefix) {
            throw new IOException("Invalid RESP2 header: expected " + prefix);
        }
            while(true){
                b = in.read();
                if (b == -1) {
                    throw new EOFException("Incomplete RESP2 header");
                }
                if(b >='0' && b <= '9') {
                    s.append((char)b);
                }else if(b == '\r'){
                    b=in.read();
                    if (b == -1) {
                        throw new EOFException("Incomplete RESP2 header");
                    }
                    if(b == '\n'){
                        break;
                    }else {
                        throw new IOException("Incomplete RESP2 header");
                    }
                }else{
                    throw new IOException("Invalid RESP2 Command");
                }
            }

        if(s.isEmpty()){
            throw new IOException("Invalid Command : missing numbers");
        }
        try{
            return Integer.parseInt(s.toString());
        }catch(NumberFormatException e){
            throw new IOException("Invalide array length" ,e);
        }
    }
}

