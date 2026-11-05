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
    private final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();
    private final Object lifeCycleLock = new Object();
    private volatile ServerSocket ss ;
    private volatile boolean stopping = false;
    private final KeyValueStore kvStore = new KeyValueStore();
    private final CommnadHandler Handler = new CommnadHandler(kvStore);
    private final ExecutorService executor = new ThreadPoolExecutor(
            4,
            4   ,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(8),
            new ThreadPoolExecutor.AbortPolicy());
    // corePoolSize = normal number of worker threads; maximumPoolSize = maximum allowed.
// Once core workers are busy, tasks are queued. If the queue is full and a new
// task arrives, extra workers are created up to maximumPoolSize; otherwise, the task is rejected.

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
                    if (stopping) {// i am adding this because of a possible race condition ,
                        //main might accept a client while the calling thread for stop has already executed as well
                        // this  closes it immediately
                        socket.close();
                        break;
                    }
                    activeClients.add(socket);

                    System.out.println("Client Connected on port:"+port);
                    try {
                        executor.submit(()->{
                            try(socket) {
                                handleClient(socket);
                            } catch (IOException e) {
                                System.err.println("Client I/O error: " + e.getMessage());
                            }finally {
                                activeClients.remove(socket);
                            }
                        });
                    }catch (RejectedExecutionException e) {
                        activeClients.remove(socket);
                        System.out.println("Queue is full");
                        socket.close();
                    }
                }

            }
        }finally {
            stop();
        }
    }

      public void handleClient(Socket socket) throws IOException {
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
           for (Socket socket : activeClients) {
               try {
                   socket.close();
               } catch (IOException e) {
                   System.err.println("Failed to Close Client: " + e.getMessage());
               }
           }
           executor.shutdown();// this allows queued tasks to execute and finish as well .
           try{
               boolean terminated = executor.awaitTermination(5,TimeUnit.SECONDS);
               if (!terminated) {
                   // returns queued clients , and doesnt wait for workers to finish task already taken
                   List<Runnable> s =executor.shutdownNow();
               }
           }catch (InterruptedException e){
               executor.shutdownNow();//executor might still be runing
               Thread.currentThread().interrupt();//restoring the flag(marking it as interrupted again )
           }finally {
               activeClients.clear();
           }

      }
}


