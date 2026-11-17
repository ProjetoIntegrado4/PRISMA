package br.edu.exemplo.ia.controller;

import br.edu.exemplo.ia.recommendation.application.RecommendCoursesUseCase;
import br.edu.exemplo.ia.recommendation.domain.AgenteIndisponivelException;
import br.edu.exemplo.ia.recommendation.infrastructure.AiServiceClient;
import br.edu.exemplo.ia.recommendation.infrastructure.OpenAiChave;
import br.edu.exemplo.ia.service.SecurityConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
@Import({SecurityConfig.class, AiServiceClient.class, OpenAiChave.class})
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
    @MockitoBean RecommendCoursesUseCase useCase;

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
        for (String path : new String[]{"profile", "predict"}) {
            mvc.perform(post("/api/agente/" + path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("/" + path));
        }
        mvc.perform(post("/api/agente/training/jobs").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.path").value("/training/jobs"));
    }
    @Test @WithMockUser(roles="ADMIN") void recommendRunsInSpring() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(useCase.recommend(any())).thenReturn((com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("{\"status\":\"generated\"}"));
        mvc.perform(post("/api/agente/recommend").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("generated"));
    }
    @Test @WithMockUser(roles="ADMIN") void openAiFailureIsSafe503() throws Exception {
        when(useCase.recommend(any())).thenThrow(new AgenteIndisponivelException("Configure OPENAI_API_KEY no arquivo .env e reinicie o backend."));
        mvc.perform(post("/api/agente/recommend").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OPENAI_API_KEY")));
    }
    @Test @WithMockUser(roles="ADMIN") void healthReportsKeyWithoutValue() throws Exception {
        mvc.perform(get("/api/agente/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openai_configured").value(false)).andExpect(jsonPath("$.provider").value("openai"));
    }
    @Test void missingTokenFailsClosed() {
        var client = new AiServiceClient("http://127.0.0.1:1", "", new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals(503, client.get("/health").getStatusCode().value());
    }
    @Test void connectionFailureIsSanitized() {
        var client = new AiServiceClient("http://127.0.0.1:1", "private-token", new com.fasterxml.jackson.databind.ObjectMapper());
        var response = client.get("/health");
        assertEquals(503, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().toString().contains("private-token"));
    }
}
