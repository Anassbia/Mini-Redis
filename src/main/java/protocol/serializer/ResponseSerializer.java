package protocol.serializer;

import protocol.response.Response;

import java.io.IOException;

public interface ResponseSerializer {
    void serialize(Response response) throws IOException;

}
