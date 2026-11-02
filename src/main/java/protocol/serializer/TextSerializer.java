package protocol.serializer;

import protocol.response.Response;

import java.io.*;

public class TextSerializer implements ResponseSerializer {
    private BufferedWriter writer;// takes java chars writes them into a buffer

    public TextSerializer(OutputStream out) {
        OutputStreamWriter osw = new OutputStreamWriter(out);
        writer = new BufferedWriter(osw);
    }

    @Override
    public void serialize(Response response) throws IOException {
        switch (response){
            case Response.SimpleString s  -> writer.write(s.value());
            case Response.BulkString b    -> writer.write(b.value());
            case Response.LongValue i     -> writer.write(Long.toString(i.value()));
            case Response.Error  e        -> writer.write(e.message());
            case Response.NullValue n     -> writer.write("NULL");
            default -> throw new IllegalStateException("Unexpected value: " + response);
        }
        writer.newLine();
        writer.flush();
    }
}
