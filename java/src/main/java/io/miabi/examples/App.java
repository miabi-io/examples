package io.miabi.examples;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** The routes, built on {@link HttpServer} so the image needs no dependencies. */
public record App(String name, String version, Map<String, String> miabi) {

    // An allow-list, not a MIABI_ prefix match: users may name their own secrets MIABI_*.
    static final List<String> MIABI_VARS = List.of(
            "MIABI_APP_NAME", "MIABI_WORKSPACE_NAME", "MIABI_STACK", "MIABI_RELEASE", "MIABI_VERSION",
            "MIABI_COMMIT", "MIABI_LOCATION", "MIABI_NODE", "MIABI_REPLICA", "MIABI_PUBLIC_URL");

    public App(String name, String version) {
        this(name, version, Map.of());
    }

    public static App fromEnv() {
        return fromEnv(System.getenv());
    }

    static App fromEnv(Map<String, String> env) {
        String version = env.get("APP_VERSION");
        if (version == null || version.isBlank()) {
            version = App.class.getPackage().getImplementationVersion();
        }
        Map<String, String> miabi = new LinkedHashMap<>();
        for (String key : MIABI_VARS) {
            String value = env.get(key);
            if (value != null && !value.isEmpty()) {
                miabi.put(key, value);
            }
        }
        return new App(
                env.getOrDefault("APP_NAME", env.getOrDefault("MIABI_APP_NAME", "java-example")),
                version == null ? "dev" : version,
                miabi);
    }

    public HttpServer start(InetSocketAddress address) throws IOException {
        HttpServer server = HttpServer.create(address, 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/healthz", get(ex -> send(ex, 200, "application/json", "{\"status\":\"ok\"}")));
        server.createContext("/api/info", get(ex -> send(ex, 200, "application/json", json(info()))));
        server.createContext("/", get(this::index));
        server.start();
        return server;
    }

    Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", name);
        info.put("version", version);
        info.put("hostname", hostname());
        info.put("java", Runtime.version().toString());
        info.put("platform", System.getProperty("os.name").toLowerCase() + "/" + System.getProperty("os.arch"));
        info.put("processors", Runtime.getRuntime().availableProcessors());
        info.put("maxHeapMiB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        info.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        info.put("miabi", miabi);
        return info;
    }

    private void index(HttpExchange ex) throws IOException {
        // The "/" context also catches every path no other context matches.
        if (!ex.getRequestURI().getPath().equals("/")) {
            send(ex, 404, "application/json", "{\"error\":\"not found\"}");
            return;
        }
        Map<String, Object> i = info();
        String release = "";
        if (miabi.containsKey("MIABI_RELEASE")) {
            release = "Release " + miabi.get("MIABI_RELEASE")
                    + (miabi.containsKey("MIABI_NODE") ? " on node " + miabi.get("MIABI_NODE") : "")
                    + (miabi.containsKey("MIABI_REPLICA") ? ", replica " + miabi.get("MIABI_REPLICA") : "");
            release = "<p>" + html(release) + "</p>\n";
        }
        String page = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>%s</title>
                <style>body{font:16px/1.5 system-ui,sans-serif;max-width:40rem;margin:4rem auto;padding:0 1rem}code{background:#f2f2f2;padding:.1rem .3rem;border-radius:4px}</style></head>
                <body>
                <h1>Hello from Java on Miabi</h1>
                <p>Version <code>%s</code> · Java %s · %s · served by <code>%s</code></p>
                %s<p>Try <a href="/api/info">/api/info</a> or <a href="/healthz">/healthz</a>.</p>
                </body>
                </html>
                """.formatted(html(i.get("app")), html(i.get("version")), html(i.get("java")),
                html(i.get("platform")), html(i.get("hostname")), release);
        send(ex, 200, "text/html; charset=utf-8", page);
    }

    private static HttpHandler get(HttpHandler next) {
        return ex -> {
            try (ex) {
                String method = ex.getRequestMethod();
                if (!method.equals("GET") && !method.equals("HEAD")) {
                    ex.getResponseHeaders().set("Allow", "GET, HEAD");
                    send(ex, 405, "application/json", "{\"error\":\"method not allowed\"}");
                    return;
                }
                next.handle(ex);
            }
        };
    }

    private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(status, head ? -1 : bytes.length);
        if (!head) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    static String json(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(e -> quote(String.valueOf(e.getKey())) + ":" + value(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String value(Object v) {
        return v instanceof Number n ? n.toString() : v instanceof Map<?, ?> m ? json(m) : quote(String.valueOf(v));
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    private static String html(Object value) {
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
