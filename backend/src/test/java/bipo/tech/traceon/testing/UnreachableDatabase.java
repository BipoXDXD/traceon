package bipo.tech.traceon.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Um endereço local que parece um banco, mas nunca serve PostgreSQL. */
public final class UnreachableDatabase implements AutoCloseable {

    public static final String CANARY_PASSWORD = "CANARY-6f1c2e0a-unreachable-db";

    private static final int BACKLOG = 50;

    private final int port;
    private final ServerSocket silentListener;

    private UnreachableDatabase(int port, ServerSocket silentListener) {
        this.port = port;
        this.silentListener = silentListener;
    }

    /** Ninguém escuta na porta: a conexão é recusada na hora. */
    public static UnreachableDatabase refusingConnections() {
        try (var socket = new ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())) {
            return new UnreachableDatabase(socket.getLocalPort(), null);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * O handshake TCP completa e o servidor nunca responde: só um timeout encerra a tentativa. O kernel aceita a
     * conexão no backlog mesmo sem {@code accept()}, e o cliente fica esperando a resposta ao startup.
     */
    public static UnreachableDatabase neverResponding() {
        try {
            var socket = new ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress());
            return new UnreachableDatabase(socket.getLocalPort(), socket);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + port + "/traceon");
        registry.add("spring.datasource.username", () -> "traceon");
        registry.add("spring.datasource.password", () -> CANARY_PASSWORD);
    }

    public int port() {
        return port;
    }

    @Override
    public void close() throws IOException {
        if (silentListener != null) {
            silentListener.close();
        }
    }
}
