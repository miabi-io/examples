package io.miabi.examples;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(properties = {
        "APP_VERSION=1.2.3", "MIABI_RELEASE=7", "MIABI_NODE=worker-2", "MIABI_SECRET_TOKEN=x"})
@AutoConfigureMockMvc
class ApplicationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthz() {
        assertThat(mvc.get().uri("/healthz"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void infoReportsVersionAndDatabase() {
        assertThat(mvc.get().uri("/api/info"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.version").isEqualTo("1.2.3");
                    json.assertThat().extractingPath("$.database").isEqualTo("H2");
                });
    }

    @Test
    void infoListsOnlyAllowedMiabiVars() {
        assertThat(mvc.get().uri("/api/info"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.miabi.MIABI_RELEASE").isEqualTo("7");
                    json.assertThat().doesNotHavePath("$.miabi.MIABI_SECRET_TOKEN");
                });
    }

    @Test
    void indexRenders() {
        assertThat(mvc.get().uri("/"))
                .hasStatusOk()
                .bodyText().contains("Hello from Spring Boot on Miabi", "<p>Release 7 on node worker-2</p>");
    }

    @Test
    void createsAndListsNotes() {
        assertThat(mvc.post().uri("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hello\"}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.body").isEqualTo("hello");

        assertThat(mvc.get().uri("/api/notes"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].body").isEqualTo("hello");
    }

    @Test
    void noteValidation() {
        assertThat(mvc.post().uri("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
