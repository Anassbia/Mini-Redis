package server;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class ClientConnectionManager {
    private final ExecutorService executor = new ThreadPoolExecutor(
            4,
            4   ,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(8),
            new ThreadPoolExecutor.AbortPolicy());
    private final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();
    private final ClientSessionHandler sessionHandler;


    public ClientConnectionManager(ClientSessionHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    public void submit(Socket socket) throws IOException {
        activeClients.add(socket);

        System.out.println("Client Connected");
        try {
            executor.submit(()->{
                try(socket) {
                    sessionHandler.handle(socket);
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

    public void shutdown(){
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
                executor.shutdownNow();
            }
        }catch (InterruptedException e){
            executor.shutdownNow();//executor might still be runing
            Thread.currentThread().interrupt();//restoring the flag(marking it as interrupted again )
        }finally {
            activeClients.clear();
        }
    }


}
