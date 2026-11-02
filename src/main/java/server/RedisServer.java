package server;

import command.CommnadHandler;
import protocol.Parser.CommandParser;
import protocol.Parser.TextParser;
import protocol.serializer.ResponseSerializer;
import protocol.serializer.TextSerializer;
import storage.KeyValueStore;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class RedisServer {
    private static final int port = 6379 ;
    private final KeyValueStore kvStore = new KeyValueStore();
    private final CommnadHandler Handler = new CommnadHandler(kvStore);

    public void start() throws IOException {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("Server Starting on port:"+port);
            try (Socket socket = ss.accept()) {
                System.out.println("Client Connected on port:"+port);
                InputStream inputStream = socket.getInputStream();//Access incoming bytes from client
                CommandParser parser = new TextParser(inputStream);
                OutputStream outputStream = socket.getOutputStream();
                ResponseSerializer serializer = new TextSerializer(outputStream);

                List<String> message ;
                while((message = parser.readCommand())!=null ){
                    System.out.println(message);
                    serializer.serialize(Handler.handle(message));
                }
            }
        }
      }
    }

