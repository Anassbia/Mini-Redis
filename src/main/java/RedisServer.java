import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RedisServer {
    private static final int port = 6379 ;
    private final KeyValueStore kvStore = new KeyValueStore();
    private CommnadHandler Handler = new CommnadHandler(kvStore);

    public void start() throws IOException {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("Server Starting on port:"+port);
            try (Socket socket = ss.accept()) {
                System.out.println("Client Connected on port:"+port);
                InputStream inputStream = socket.getInputStream();//Access incoming bytes from client

                InputStreamReader inputStreamReader =
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8);//Decodes bytes into chars(UtF8)

                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);//read text buffer

                OutputStream outputStream = socket.getOutputStream();
                OutputStreamWriter outputStreamWriter =
                        new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);

                String message;

                while((message = bufferedReader.readLine())!=null){
                    System.out.println(message);
                    bufferedWriter.write(Handler.handle(message));
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                }


            }
        }
      }
    }

