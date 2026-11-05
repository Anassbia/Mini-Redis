import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real TCP integration tests for Milestone 3 (a fixed pool of 4 workers).
 *
 * Starts Mini-Redis in a SEPARATE JVM because start() currently runs forever.
 * This lets the tests stop the server without requiring changes to production code.
 *
 * Replaces the Milestone 2.5 RedisServerIntegrationTest, which expected the
 * single-client server to terminate after the first disconnection.
 */
class RedisServerIntegrationTest {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6379;
    private static final int POOL_SIZE = 4;
    private static Process serverProcess;
    private static Path serverLog;

    @BeforeAll
    static void launchServer() throws Exception {
        // A pre-existing server on port 6379 would make these tests misleading.
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(HOST, PORT));
        } catch (BindException e) {
            fail("Port 6379 is already in use. Stop the Mini-Redis process in IntelliJ "
                    + "before running this test class.", e);
        }

        // Production classes use only the JDK, so their compiled directory is
        // sufficient as the child JVM classpath (works in IntelliJ and Maven).
        String appClasspath = Paths.get(
                Class.forName("Main").getProtectionDomain().getCodeSource()
                        .getLocation().toURI()).toString();
        String javaExe = Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "java.exe" : "java").toString();

        serverLog = Files.createTempFile("miniredis-m3-server-", ".log");
        serverLog.toFile().deleteOnExit(); // Fallback if Windows keeps the redirected log locked.
        serverProcess = new ProcessBuilder(javaExe, "-cp", appClasspath, "Main")
                .redirectErrorStream(true)
                .redirectOutput(serverLog.toFile())
                .start();

        try (Client ignored = connectWithRetry()) {
            // The server successfully started and accepted a TCP connection.
        } catch (Exception e) {
            String output = Files.exists(serverLog) ? Files.readString(serverLog) : "(no log)";
            stopServer();
            throw new AssertionError("Mini-Redis did not start. Server output:\n"
                    + output, e);
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            if (!serverProcess.waitFor(3, TimeUnit.SECONDS)) {
                serverProcess.destroyForcibly();
                serverProcess.waitFor(3, TimeUnit.SECONDS);
            }
        }
        if (serverLog != null) {
            try {
                Files.deleteIfExists(serverLog);
            } catch (java.nio.file.FileSystemException lockedLog) {
                // Windows may keep a ProcessBuilder-redirected file locked briefly.
                // The log is temporary; cleanup must not fail otherwise-passing tests.
                // deleteOnExit() was registered when the file was created.
            }
        }
    }

    @Test
    @Timeout(10)
    void existingCommandsStillWorkOverTcp() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String name = "m3name" + suffix;
        String counter = "m3counter" + suffix;

        try (Client client = connectWithRetry()) {
            assertEquals("OK", client.command("SET " + name + " Anas"));
            assertEquals("Anas", client.command("GET " + name));
            assertEquals("0", client.command("SETNX " + name + " Other"));
            assertEquals("1", client.command("INCR " + counter));
            assertEquals("2", client.command("INCR " + counter));
            assertEquals("1", client.command("DECR " + counter));
            assertEquals("1", client.command("DEL " + name));
            assertEquals("NULL", client.command("GET " + name));
            assertEquals("ERR empty command", client.command(""));
            assertEquals("ERR unknown command", client.command("NOTACOMMAND"));
            assertEquals("ERR wrong number of arguments", client.command("GET"));
        }
    }

    @Test
    @Timeout(10)
    void idleClientDoesNotBlockAnotherClientAndTheyShareStorage() throws Exception {
        String key = "m3shared" + UUID.randomUUID().toString().replace("-", "");

        try (Client clientA = connectWithRetry();
             Client clientB = connectWithRetry()) {
            // Client A stays connected and idle throughout B's request.
            assertEquals("OK", clientB.command("SET " + key + " fromB"));
            assertEquals("fromB", clientA.command("GET " + key));
            assertEquals("1", clientB.command("DEL " + key));
            assertEquals("NULL", clientA.command("GET " + key));
        }
    }

    @Test
    @Timeout(20)
    void fourClientsCanIncrementTheSameKeyConcurrently() throws Exception {
        String key = "m3atomic" + UUID.randomUUID().toString().replace("-", "");
        int incrementsPerClient = 30; // smoke test; heavier load belongs to Milestone 4

        ExecutorService testClients = Executors.newFixedThreadPool(POOL_SIZE);
        CountDownLatch allClientsReady = new CountDownLatch(POOL_SIZE);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();

        try {
            for (int i = 0; i < POOL_SIZE; i++) {
                tasks.add(testClients.submit(() -> {
                    try (Client client = connectWithRetry()) {
                        allClientsReady.countDown();
                        if (!startTogether.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting for test start");
                        }
                        for (int n = 0; n < incrementsPerClient; n++) {
                            String reply = client.command("INCR " + key);
                            assertNotNull(reply, "Server disconnected during INCR");
                            assertFalse(reply.startsWith("ERR"), "INCR failed: " + reply);
                        }
                        return null;
                    }
                }));
            }

            assertTrue(allClientsReady.await(5, TimeUnit.SECONDS),
                    "Not all four clients connected");
            startTogether.countDown();
            for (Future<?> task : tasks) {
                task.get(8, TimeUnit.SECONDS);
            }

            try (Client checker = connectWithRetry()) {
                assertEquals(Integer.toString(POOL_SIZE * incrementsPerClient),
                        checker.command("GET " + key),
                        "Concurrent INCR operations lost updates");
            }
        } finally {
            startTogether.countDown();
            testClients.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void fifthClientWaitsForWorkerThenProceedsWhenOneIsFree() throws Exception {
        List<Client> idleClients = new ArrayList<>();
        try {
            // Sending one command confirms that each connection is actively
            // being handled before we leave it idle inside readCommand().
            for (int i = 0; i < POOL_SIZE; i++) {
                Client client = connectWithRetry();
                idleClients.add(client);
                assertEquals("NULL", client.command("GET m3absent" + i));
            }

            try (Client fifth = connectWithRetry()) {
                fifth.writeCommand("GET m3stillabsent");
                fifth.socket.setSoTimeout(300);
                assertThrows(SocketTimeoutException.class, fifth::readReply,
                        "Fifth client unexpectedly received a worker while four were idle");

                // Disconnect one idle client: its worker should take the queued task.
                idleClients.get(0).close();
                fifth.socket.setSoTimeout(3000);
                assertEquals("NULL", fifth.readReply());
            }
        } finally {
            for (Client client : idleClients) {
                client.close();
            }
        }
    }

    private static Client connectWithRetry() throws Exception {
        IOException last = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(HOST, PORT), 300);
                socket.setSoTimeout(3000);
                return new Client(socket);
            } catch (IOException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw new IOException("Could not connect to Mini-Redis on port 6379", last);
    }

    private static final class Client implements AutoCloseable {
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

        private String command(String command) throws IOException {
            writeCommand(command);
            return readReply();
        }

        private void writeCommand(String command) throws IOException {
            writer.write(command);
            writer.newLine();
            writer.flush();
        }

        private String readReply() throws IOException {
            return reader.readLine();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
