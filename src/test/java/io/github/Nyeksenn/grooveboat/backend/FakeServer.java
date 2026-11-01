package io.github.Nyeksenn.grooveboat.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal loopback HTTP server for backend tests. Records every request URI so tests can inspect them. */
public final class FakeServer implements AutoCloseable {
    /** A canned response. */
    public record Reply(int status, String contentType, byte[] body) {
        public static Reply json(String body) {
            return new Reply(200, "application/json", body.getBytes(StandardCharsets.UTF_8));
        }

        public static Reply status(int status) {
            return new Reply(status, "text/plain", new byte[0]);
        }
    }

    /** Decides the response for a request. */
    public interface Router {
        Reply route(String method, String path, String query);
    }

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    public FakeServer(Router router) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        HttpHandler handler = exchange -> respond(exchange, router);
        server.createContext("/", handler);
        server.start();
    }

    private void respond(HttpExchange exchange, Router router) throws IOException {
        try (exchange) {
            var uri = exchange.getRequestURI();
            requests.add(exchange.getRequestMethod() + " " + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            var reply = router.route(exchange.getRequestMethod(), uri.getPath(), uri.getRawQuery() == null ? "" : uri.getRawQuery());
            exchange.getResponseHeaders().set("Content-Type", reply.contentType());
            exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? -1 : reply.body().length);
            if (reply.body().length > 0) {
                exchange.getResponseBody().write(reply.body());
            }
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** All requests seen so far, as {@code "METHOD /path?query"}. */
    public List<String> requests() {
        return requests;
    }

    /** The {@code Authorization} header of each request (the string {@code "null"} when absent), in request order. */
    public List<String> authorizations() {
        return authorizations;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
