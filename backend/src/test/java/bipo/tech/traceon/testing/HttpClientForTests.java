package bipo.tech.traceon.testing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Requisições HTTP reais contra o servidor do teste, como o proxy do Vite faria. */
public final class HttpClientForTests {

    // Uma requisição presa falha o teste em vez de travar a execução.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private HttpClientForTests() {}

    public static HttpResponse<String> get(int port, String path) {
        return send(port, "GET", path);
    }

    public static HttpResponse<String> send(int port, String method, String path) {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(REQUEST_TIMEOUT)
                .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new AssertionError("request failed: " + method + " " + path, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("request interrupted: " + method + " " + path, exception);
        }
    }
}
