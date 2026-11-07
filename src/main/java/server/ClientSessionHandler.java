package server;

import command.CommnadHandler;
import protocol.Parser.CommandParser;
import protocol.Parser.Resp2Parser;
import protocol.Parser.TextParser;
import protocol.serializer.Resp2Serializer;
import protocol.serializer.ResponseSerializer;
import protocol.serializer.TextSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.util.List;

public class ClientSessionHandler {
    private final CommnadHandler commnadHandler;

    public ClientSessionHandler(CommnadHandler commnadHandler) {
        this.commnadHandler = commnadHandler;
    }

    public void handle(Socket socket) throws IOException {
        PushbackInputStream inputStream = new PushbackInputStream(socket.getInputStream(),1);
        int first = inputStream.read();
        if(first==-1){
            return;
        }
        inputStream.unread(first);
        CommandParser parser;
        OutputStream outputStream;
        ResponseSerializer serializer;
        outputStream = socket.getOutputStream();
        if(first != '*'){
            parser = new TextParser(inputStream);
            serializer = new TextSerializer(outputStream);
        }else{
            parser = new Resp2Parser(inputStream);
            serializer = new Resp2Serializer(outputStream);
        }
        List<String> message ;
        while((message = parser.readCommand())!=null ){
            System.out.println(message);
            serializer.serialize(commnadHandler.handle(message));
        }
    }
}
