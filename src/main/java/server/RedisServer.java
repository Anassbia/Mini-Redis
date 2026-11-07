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
import java.net.SocketException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class RedisServer {
    private static final int port = 6379 ;
    private final Object lifeCycleLock = new Object();
    private volatile ServerSocket ss ;
    private volatile boolean stopping = false;
    private final KeyValueStore kvStore = new KeyValueStore();
    private final ClientSessionHandler sessionHandler = new ClientSessionHandler(new CommnadHandler(kvStore));
    private final ClientConnectionManager connectionManager = new ClientConnectionManager(sessionHandler);

    //ugly function , to refactor later ,clean code not respected
    public void start() throws IOException {

        try {
            ServerSocket serverSocket ;
            synchronized (lifeCycleLock) {
                if (stopping) {
                    return;
                }
                serverSocket = new ServerSocket(port);
                ss = serverSocket;
                System.out.println("Server Starting on port:"+port);
            }
            while (!stopping) {
                Socket socket ;
                try{
                    socket = ss.accept();
                }catch (SocketException e){
                 if(stopping){
                     break;
                 }
                 else{
                     throw e;
                 }
                }
                synchronized (lifeCycleLock) {
                    if (stopping) {
                        socket.close();
                        break;
                    }
                    connectionManager.submit(socket);
                }

            }
        }finally {
            stop();
        }
    }
      public void stop() throws IOException {
       synchronized (lifeCycleLock) {
           if (stopping) {
               return;
           }
           stopping = true;
       }
           if (ss!=null){
               try{
                   ss.close();
               }catch (IOException e){
                   System.err.println("Error closing Server: "+e.getMessage());
               }
           }
           connectionManager.shutdown();

      }
}


