import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import server.RedisServer;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class RedisServerShutdownTest {

    private static final int PORT = 6379;

    @Test
    void stopBeforeStartShouldPreventBinding() throws Exception {
        RedisServer server = new RedisServer();

        server.stop();

        // Occupy the port deliberately.
        // start() should not attempt to bind after stop().
        try (ServerSocket occupied = new ServerSocket(PORT)) {
            assertDoesNotThrow(server::start);
            assertDoesNotThrow(server::stop);
        }
    }

    @Test
    void stopShouldCloseIdleClientAndTerminateServer()
            throws Exception {

        RedisServer server = new RedisServer();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread serverThread = launch(server, error);

        try (Socket client = connectEventually()) {

            // No commands: worker waits for client input.
            server.stop();

            assertDisconnected(client);

        } finally {
            server.stop();
            serverThread.join(2000);
        }

        assertFalse(serverThread.isAlive(),
                "Server thread is still running after stop()");
        assertNull(error.get(),
                "Server failed: " + error.get());
    }

    @Test
    void stopShouldCloseManyClientConnections()
            throws Exception {

        RedisServer server = new RedisServer();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread serverThread = launch(server, error);

        List<Socket> clients = new ArrayList<>();

        try {
            // Our pool supports 4 running + 8 queued tasks.
            for (int i = 0; i < 12; i++) {
                clients.add(connectEventually());
            }

            // Give the accept loop time to register connections.
            Thread.sleep(300);

            server.stop();

            for (Socket client : clients) {
                assertDisconnected(client);
            }

        } finally {
            for (Socket client : clients) {
                client.close();
            }

            server.stop();
            serverThread.join(2000);
        }

        assertFalse(serverThread.isAlive(),
                "Server did not terminate");
        assertNull(error.get(),
                "Server failed: " + error.get());
    }

    private static Thread launch(
            RedisServer server,
            AtomicReference<Throwable> error) {

        Thread thread = new Thread(() -> {
            try {
                server.start();
            } catch (Throwable t) {
                error.set(t);
            }
        }, "redis-test-server");

        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static Socket connectEventually() throws Exception {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(3);

        IOException lastError = null;

        while (System.nanoTime() < deadline) {
            Socket socket = new Socket();

            try {
                socket.connect(
                        new InetSocketAddress(
                                InetAddress.getLoopbackAddress(), PORT),
                        200
                );
                socket.setSoTimeout(2000);
                return socket;

            } catch (IOException e) {
                lastError = e;
                socket.close();
                Thread.sleep(20);
            }
        }

        throw new AssertionError(
                "Could not connect to Mini-Redis", lastError);
    }

    private static void assertDisconnected(Socket socket)
            throws IOException {

        try {
            assertEquals(-1, socket.getInputStream().read(),
                    "Client connection was not closed");
        } catch (SocketException e) {
            // Connection reset also means the peer disconnected.
        }
    }
}
