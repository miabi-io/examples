package io.miabi.examples;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
class InfoController {

    // An allow-list, not a MIABI_ prefix match: users may name their own secrets MIABI_*.
    private static final List<String> MIABI_VARS = List.of(
            "MIABI_APP_NAME", "MIABI_WORKSPACE_NAME", "MIABI_STACK", "MIABI_RELEASE", "MIABI_VERSION",
            "MIABI_COMMIT", "MIABI_LOCATION", "MIABI_NODE", "MIABI_REPLICA", "MIABI_PUBLIC_URL");

    private final String app;
    private final String version;
    private final String database;
    private final Map<String, String> miabi = new LinkedHashMap<>();

    InfoController(
            @Value("${APP_NAME:${MIABI_APP_NAME:spring-boot-example}}") String app,
            @Value("${APP_VERSION:}") String version,
            ObjectProvider<BuildProperties> build,
            DataSource dataSource,
            Environment env) {
        this.app = app;
        this.version = !version.isBlank() ? version
                : build.getIfAvailable() != null ? build.getIfAvailable().getVersion() : "dev";
        this.database = new JdbcTemplate(dataSource)
                .execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        for (String key : MIABI_VARS) {
            String value = env.getProperty(key, "");
            if (!value.isEmpty()) {
                miabi.put(key, value);
            }
        }
    }

    @GetMapping("/api/info")
    Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", app);
        info.put("version", version);
        info.put("hostname", hostname());
        info.put("java", Runtime.version().toString());
        info.put("database", database);
        info.put("processors", Runtime.getRuntime().availableProcessors());
        info.put("maxHeapMiB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        info.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        info.put("miabi", miabi);
        return info;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    String index() {
        Map<String, Object> i = info();
        String db = "PostgreSQL".equals(database)
                ? "connected to Postgres"
                : "using an in-memory " + database + " database";
        String release = "";
        if (miabi.containsKey("MIABI_RELEASE")) {
            release = "Release " + miabi.get("MIABI_RELEASE")
                    + (miabi.containsKey("MIABI_NODE") ? " on node " + miabi.get("MIABI_NODE") : "")
                    + (miabi.containsKey("MIABI_REPLICA") ? ", replica " + miabi.get("MIABI_REPLICA") : "");
            release = "<p>" + html(release) + "</p>\n";
        }
        return """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>%s</title>
                <style>body{font:16px/1.5 system-ui,sans-serif;max-width:40rem;margin:4rem auto;padding:0 1rem}code{background:#f2f2f2;padding:.1rem .3rem;border-radius:4px}</style></head>
                <body>
                <h1>Hello from Spring Boot on Miabi</h1>
                <p>Version <code>%s</code> · Java %s · %s · served by <code>%s</code></p>
                %s<p>Try <a href="/api/info">/api/info</a>, <a href="/api/notes">/api/notes</a> or <a href="/healthz">/healthz</a>.</p>
                </body>
                </html>
                """.formatted(
                html(i.get("app")), html(i.get("version")), html(i.get("java")), db, html(i.get("hostname")), release);
    }

    private static String html(Object value) {
        return HtmlUtils.htmlEscape(String.valueOf(value));
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
