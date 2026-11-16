package br.edu.exemplo.ia.controller;

import br.edu.exemplo.ia.service.SecurityConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgenteController.class)
@Import(SecurityConfig.class)
class AgenteControllerTest {
    static HttpServer server;
    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                boolean authorized = "test-token".equals(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
                String json = authorized ? "{\"status\":\"ok\",\"path\":\"" + path + "\"}" : "{}";
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(authorized ? (path.endsWith("/jobs") ? 202 : 200) : 401, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("AI_SERVICE_URL", () -> "http://127.0.0.1:" + server.getAddress().getPort());
        registry.add("AI_INTERNAL_TOKEN", () -> "test-token");
    }
    @AfterAll static void stop() { server.stop(0); }
    @Autowired MockMvc mvc;

    @Test void anonymousDenied() throws Exception { mvc.perform(get("/api/agente/options")).andExpect(status().isUnauthorized()); }
    @Test @WithMockUser void nonAdminDenied() throws Exception { mvc.perform(get("/api/agente/options")).andExpect(status().isForbidden()); }
    @Test @WithMockUser(roles="ADMIN") void csrfRequired() throws Exception {
        mvc.perform(post("/api/agente/training/jobs").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="ADMIN") void forwardsReadAndJob() throws Exception {
        mvc.perform(get("/api/agente/options").param("company", "C01")).andExpect(status().isOk()).andExpect(jsonPath("$.path").value("/options"));
        mvc.perform(get("/api/agente/health")).andExpect(status().isOk());
        mvc.perform(get("/api/agente/jobs/00000000-0000-0000-0000-000000000001")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles="ADMIN") void forwardsActions() throws Exception {
        for (String path : new String[]{"profile", "recommend", "predict"}) {
            mvc.perform(post("/api/agente/" + path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("/" + path));
        }
        for (String path : new String[]{"training/jobs", "index/jobs", "evaluation/jobs"}) {
            mvc.perform(post("/api/agente/" + path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.path").value("/" + path));
        }
    }
    @Test void missingTokenFailsClosed() {
        var controller = new AgenteController("http://127.0.0.1:1", "", new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals(503, controller.health().getStatusCode().value());
    }
    @Test void connectionFailureIsSanitized() {
        var controller = new AgenteController("http://127.0.0.1:1", "private-token", new com.fasterxml.jackson.databind.ObjectMapper());
        var response = controller.health();
        assertEquals(503, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().toString().contains("private-token"));
    }
}
