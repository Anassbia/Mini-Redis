package server;

import command.CommnadHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real loopback TCP + RESP2, without starting RedisServer on fixed port 6379. */
class Resp2TtlSessionIntegrationTest {

    private static String cmd(String... args) {
        StringBuilder wire = new StringBuilder("*" + args.length + "\r\n");
        for (String arg : args) {
            wire.append('$').append(arg.getBytes(StandardCharsets.UTF_8).length)
                    .append("\r\n").append(arg).append("\r\n");
        }
        return wire.toString();
    }

    private static long integerLine(String line) {
        assertNotNull(line);
        assertTrue(line.startsWith(":"), "Expected RESP2 integer, got: " + line);
        return Long.parseLong(line.substring(1));
    }

    @Test @Timeout(10)
    void pipelinedSetExpireTtlGetWorksOnSameConnection() throws Exception {
        try (Session session = Session.open()) {
            session.send(cmd("SET", "key", "Anas")
                    + cmd("EXPIRE", "key", "60")
                    + cmd("TTL", "key")
                    + cmd("GET", "key"));
            assertEquals("+OK", session.line());
            assertEquals(1L, integerLine(session.line()));
            long ttl = integerLine(session.line());
            assertTrue(ttl >= 58 && ttl <= 60, "Unexpected TTL: " + ttl);
            assertEquals("$4", session.line());
            assertEquals("Anas", session.line());
            session.finishNormally();
        }
    }

    @Test @Timeout(10)
    void ttlReturnsMinusOneAndMinusTwoOverResp2() throws Exception {
        try (Session session = Session.open()) {
            session.send(cmd("SET", "permanent", "v")
                    + cmd("TTL", "permanent")
                    + cmd("TTL", "unknown"));
            assertEquals("+OK", session.line());
            assertEquals(-1L, integerLine(session.line()));
            assertEquals(-2L, integerLine(session.line()));
            session.finishNormally();
        }
    }

    @Test @Timeout(10)
    void expireZeroDeletesAndReturnsIntegerOne() throws Exception {
        try (Session session = Session.open()) {
            session.send(cmd("SET", "temp", "v")
                    + cmd("EXPIRE", "temp", "0")
                    + cmd("GET", "temp")
                    + cmd("EXPIRE", "temp", "0"));
            assertEquals("+OK", session.line());
            assertEquals(1L, integerLine(session.line()));
            assertEquals("$-1", session.line());
            assertEquals(0L, integerLine(session.line()));
            session.finishNormally();
        }
    }

    @Test @Timeout(10)
    void invalidExpireArgumentReturnsErrorButConnectionStaysAlive() throws Exception {
        try (Session session = Session.open()) {
            session.send(cmd("SET", "key", "v")
                    + cmd("EXPIRE", "key", "abc")
                    + cmd("GET", "key"));
            assertEquals("+OK", session.line());
            String error = session.line();
            assertNotNull(error);
            assertTrue(error.startsWith("-ERR"), "Expected RESP error, got: " + error);
            assertEquals("$1", session.line());
            assertEquals("v", session.line());
            session.finishNormally();
        }
    }

    private static final class Session implements AutoCloseable {
        final Socket client;
        final Socket serverSide;
        final ExecutorService workers;
        final Future<?> task;
        final BufferedReader reader;

        private Session(Socket client, Socket serverSide, ExecutorService workers, Future<?> task) throws IOException {
            this.client = client;
            this.serverSide = serverSide;
            this.workers = workers;
            this.task = task;
            this.reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        }

        static Session open() throws IOException {
            try (ServerSocket listener = new ServerSocket(0, 16, InetAddress.getLoopbackAddress())) {
                Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                Socket serverSide = listener.accept();
                client.setSoTimeout(4000);
                ExecutorService workers = Executors.newSingleThreadExecutor();
                ClientSessionHandler handler = new ClientSessionHandler(
                        new CommnadHandler(new KeyValueStore()));
                Future<?> task = workers.submit(() -> {
                    try (serverSide) {
                        handler.handle(serverSide);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                return new Session(client, serverSide, workers, task);
            }
        }

        void send(String wire) throws IOException {
            OutputStream out = client.getOutputStream();
            out.write(wire.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        String line() throws IOException { return reader.readLine(); }

        void finishNormally() throws Exception {
            client.shutdownOutput();
            task.get(4, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            try {
                client.close();
            } finally {
                serverSide.close();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(4, TimeUnit.SECONDS),
                        "Session worker failed to terminate");
            }
        }
    }
}
