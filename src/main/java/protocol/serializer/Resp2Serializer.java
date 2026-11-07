package protocol.serializer;

import protocol.response.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Resp2Serializer implements ResponseSerializer {
    private final OutputStream out;

    public Resp2Serializer(OutputStream out) {
        this.out = out;
    }
    private void writeUtf8(String value ) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void serialize(Response response) throws IOException {
        switch (response){
            case Response.SimpleString s -> writeUtf8("+"+s.value()+"\r\n");
            case Response.BulkString s -> {
                byte[] b = s.value().getBytes(StandardCharsets.UTF_8);
                writeUtf8("$"+b.length+"\r\n");
                out.write(b);
                writeUtf8("\r\n");
            }
            case Response.LongValue i ->   writeUtf8(":"+i.value()+"\r\n");
            case Response.Error e -> writeUtf8("-"+e.message()+"\r\n");
            case Response.NullValue n -> writeUtf8("$-1\r\n");
            default -> throw new IllegalStateException("Unexpected value: " + response);
        }
        out.flush();
    }
}
