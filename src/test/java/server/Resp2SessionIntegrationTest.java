package server;

import command.CommnadHandler;
import org.junit.jupiter.api.Test;
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

/**
 * Real TCP loopback tests for ClientSessionHandler.
 * Uses an ephemeral port instead of RedisServer's fixed 6379 to avoid colliding
 * with the existing server integration suite or a local Redis installation.
 */
class Resp2SessionIntegrationTest {

    private static String command(String... args) {
        StringBuilder wire = new StringBuilder("*" + args.length + "\r\n");
        for (String arg : args) {
            wire.append('$').append(arg.getBytes(StandardCharsets.UTF_8).length)
                    .append("\r\n").append(arg).append("\r\n");
        }
        return wire.toString();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void send(Socket client, String wire) throws IOException {
        OutputStream output = client.getOutputStream();
        output.write(utf8(wire));
        output.flush();
    }

    private static void expectWire(Socket client, String expected) throws IOException {
        byte[] expectedBytes = utf8(expected);
        byte[] actualBytes = client.getInputStream().readNBytes(expectedBytes.length);
        assertArrayEquals(expectedBytes, actualBytes);
    }

    @Test
    void exchangesPipelinedSetAndGetOverRealTcp() throws Exception {
        try (Session session = Session.open()) {
            send(session.client, command("SET", "name", "Anas") + command("GET", "name"));
            expectWire(session.client, "+OK\r\n$4\r\nAnas\r\n");
            session.finishNormally();
        }
    }

    @Test
    void sharesStorageAcrossCommandsAndSerializesIntegers() throws Exception {
        try (Session session = Session.open()) {
            send(session.client, command("INCR", "counter")
                    + command("INCR", "counter")
                    + command("GET", "counter")
                    + command("DEL", "counter")
                    + command("GET", "counter"));
            expectWire(session.client, ":1\r\n:2\r\n$1\r\n2\r\n:1\r\n$-1\r\n");
            session.finishNormally();
        }
    }

    @Test
    void roundTripsUnicodeAndEmbeddedNewlines() throws Exception {
        try (Session session = Session.open()) {
            send(session.client, command("SET", "clé", "a\r\nbé") + command("GET", "clé"));
            // a(1) + CRLF(2) + b(1) + é(2 UTF-8 bytes) = 6 bytes
            expectWire(session.client, "+OK\r\n$6\r\na\r\nbé\r\n");
            session.finishNormally();
        }
    }

    @Test
    void preservesExistingPlainTextProtocol() throws Exception {
        try (Session session = Session.open()) {
            send(session.client, "SET name Anas\nGET name\n");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(session.client.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("OK", reader.readLine());
            assertEquals("Anas", reader.readLine());
            session.finishNormally();
        }
    }

    @Test
    void emptyConnectionEndsSessionWithoutFailure() throws Exception {
        try (Session session = Session.open()) {
            session.finishNormally();
        }
    }

    private static final class Session implements AutoCloseable {
        final Socket client;
        final Socket serverSide;
        final ExecutorService workers;
        final Future<?> task;

        private Session(Socket client, Socket serverSide, ExecutorService workers, Future<?> task) {
            this.client = client;
            this.serverSide = serverSide;
            this.workers = workers;
            this.task = task;
        }

        static Session open() throws IOException {
            try (ServerSocket listener = new ServerSocket(0, 16, InetAddress.getLoopbackAddress())) {
                Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                Socket serverSide = listener.accept();
                client.setSoTimeout(3000);
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

        void finishNormally() throws Exception {
            client.shutdownOutput();
            task.get(3, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            try {
                client.close();
            } finally {
                serverSide.close();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS),
                        "Session test worker did not terminate");
            }
        }
    }
}
