
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import server.RedisServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class RedisServerIntegrationTest {

    @Test
    @Timeout(15)
    void testMultipleCommandsOverTcp() throws Exception {

        ExecutorService executor =
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "test-server");
                    t.setDaemon(true);
                    return t;
                });

        Future<?> serverTask = executor.submit(() -> {
            new RedisServer().start();
            return null;
        });

        try {
            try (Socket socket = connectWithRetry()) {

                socket.setSoTimeout(3000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                // SET and GET
                check(writer, reader, "SET name Anas", "OK");
                check(writer, reader, "GET name", "Anas");

                // Case insensitivity
                check(writer, reader, "set city Rabat", "OK");
                check(writer, reader, "get city", "Rabat");

                // INCR and DECR
                check(writer, reader, "INCR counter", "1");
                check(writer, reader, "INCR counter", "2");
                check(writer, reader, "DECR counter", "1");
                check(writer, reader, "GET counter", "1");

                // SETNX
                check(writer, reader, "SETNX name Ali", "0");
                check(writer, reader, "GET name", "Anas");
                check(writer, reader, "SETNX newkey value", "1");

                // DEL
                check(writer, reader, "DEL name", "1");
                check(writer, reader, "DEL name", "0");
                check(writer, reader, "GET name", "NULL");

                // Invalid commands
                check(writer, reader, "GET",
                        "ERR wrong number of arguments");

                check(writer, reader, "HELLO",
                        "ERR unknown command");

                check(writer, reader, "INCR city",
                        "ERR value is not an integer");

                // Invalid increment must not change stored data
                check(writer, reader, "GET city", "Rabat");
            }

            // Server should terminate after client disconnects
            serverTask.get(3, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }
    }

    private void check(
            BufferedWriter writer,
            BufferedReader reader,
            String command,
            String expected
    ) throws IOException {

        writer.write(command);
        writer.newLine();
        writer.flush();

        String actual = reader.readLine();

        assertEquals(expected, actual,
                "Failed command: " + command);
    }

    private Socket connectWithRetry() throws Exception {

        IOException lastError = null;

        for (int i = 0; i < 30; i++) {
            Socket socket = new Socket();

            try {
                socket.connect(
                        new InetSocketAddress("127.0.0.1", 6379),
                        300
                );
                return socket;

            } catch (IOException e) {
                lastError = e;
                socket.close();
                Thread.sleep(100);
            }
        }

        throw new IOException(
                "Could not connect to Mini-Redis",
                lastError
        );
    }
}
