package com.elyadata.feature.job.e2e;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;


class LocalFixtureServer implements AutoCloseable {

    private final HttpServer server;

    private LocalFixtureServer(HttpServer server) {
        this.server = server;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    static LocalFixtureServer startScrapeFixture() {
        HttpServer server = create();
        server.createContext("/", exchange -> respond(exchange, """
                <html><body>
                  <h1 id="title">Distributed Scrape Fixture</h1>
                  <span class="price">42.00</span>
                </body></html>
                """));
        server.start();
        return new LocalFixtureServer(server);
    }

    static LocalFixtureServer startCrawlFixture() {
        HttpServer server = create();
        Map<String, String> pages = Map.of(
                "/", """
                        <html><body>
                          <h1>Root</h1>
                          <a href="/page/1">p1</a>
                          <a href="/page/2">p2</a>
                          <a href="/page/3">p3</a>
                        </body></html>
                        """,
                "/page/1", "<html><body><h1>Page One</h1></body></html>",
                "/page/2", "<html><body><h1>Page Two</h1></body></html>",
                "/page/3", "<html><body><h1>Page Three</h1></body></html>"
        );
        pages.forEach((path, html) -> server.createContext(path, exchange -> respond(exchange, html)));
        server.start();
        return new LocalFixtureServer(server);
    }

    private static HttpServer create() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start local fixture server", e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
