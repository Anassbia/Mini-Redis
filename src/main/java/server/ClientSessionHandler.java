package server;

import command.CommnadHandler;
import protocol.Parser.CommandParser;
import protocol.Parser.TextParser;
import protocol.serializer.ResponseSerializer;
import protocol.serializer.TextSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

public class ClientSessionHandler {
    private final CommnadHandler commnadHandler;

    public ClientSessionHandler(CommnadHandler commnadHandler) {
        this.commnadHandler = commnadHandler;
    }

    public void handle(Socket socket) throws IOException {
        InputStream inputStream = socket.getInputStream();//Access incoming bytes from client
        CommandParser parser = new TextParser(inputStream);
        OutputStream outputStream = socket.getOutputStream();
        ResponseSerializer serializer = new TextSerializer(outputStream);

        List<String> message ;
        while((message = parser.readCommand())!=null ){
            System.out.println(message);
            serializer.serialize(commnadHandler.handle(message));
        }
    }
}
