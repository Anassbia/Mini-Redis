package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Shared TCP fixture for M4 tests. Uses the project's existing line-based text protocol. */
final class RedisStressSupport {
    static final int PORT = 6379;
    private static final String HOST = "127.0.0.1";

    private RedisStressSupport() { }

    static RunningServer startServer() throws Exception {
        // Fail rather than accidentally testing another local Redis process.
        try (ServerSocket probe = new ServerSocket(PORT)) {
            // The port must be available for this test instance.
        } catch (IOException e) {
            throw new AssertionError("Port 6379 is occupied. Stop other Redis/Mini-Redis processes.", e);
        }

        RedisServer server = new RedisServer();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                server.start();
            } catch (Throwable t) {
                unexpected.set(t);
            }
        }, "mini-redis-m4-server");
        thread.setDaemon(true); // A failed test should not leave the test JVM hanging.
        thread.start();

        try (Client probe = connect()) {
            assertEquals("NULL", probe.command("GET m4_server_readiness_probe"));
        } catch (Exception e) {
            server.stop();
            thread.join(8_000);
            throw e;
        }
        return new RunningServer(server, thread, unexpected);
    }

    static Client connect() throws Exception {
        IOException last = null;
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(HOST, PORT), 500);
                socket.setSoTimeout(6_000);
                return new Client(socket);
            } catch (IOException e) {
                last = e;
                socket.close();
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Mini-Redis did not start listening on port 6379", last);
    }

    static final class RunningServer implements AutoCloseable {
        final RedisServer server;
        private final Thread thread;
        private final AtomicReference<Throwable> unexpected;

        private RunningServer(RedisServer server, Thread thread,
                              AtomicReference<Throwable> unexpected) {
            this.server = server;
            this.thread = thread;
            this.unexpected = unexpected;
        }

        @Override
        public void close() throws Exception {
            server.stop();
            thread.join(8_000);
            assertFalse(thread.isAlive(), "Server accept thread did not terminate");
            Throwable error = unexpected.get();
            assertNull(error, () -> "Unexpected server failure: " + error);
        }
    }

    static final class Client implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private Client(Socket socket) throws IOException {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        String command(String command) throws IOException {
            writer.write(command);
            writer.newLine();
            writer.flush();
            return reader.readLine();
        }

        /** After stop(), EOF or a connection reset both count as disconnected. */
        void assertDisconnected() throws IOException {
            try {
                String line = reader.readLine();
                assertNull(line, "Expected closed connection, but received: " + line);
            } catch (java.net.SocketException expected) {
                // TCP peer may reset rather than close gracefully.
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
