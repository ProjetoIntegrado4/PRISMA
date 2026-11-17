package br.edu.exemplo.ia.evaluation;

import br.edu.exemplo.ia.evaluation.web.EvaluationController;
import br.edu.exemplo.ia.recommendation.infrastructure.AiServiceClient;
import br.edu.exemplo.ia.recommendation.infrastructure.RecomendacaoRepository;
import br.edu.exemplo.ia.service.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluationController.class)
@Import({SecurityConfig.class, AiServiceClient.class})
class EvaluationControllerTest {
    static final AtomicReference<String> lastBody = new AtomicReference<>("");
    static HttpServer ragas;
    static {
        try {
            ragas = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ragas.createContext("/", exchange -> {
                lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String path = exchange.getRequestURI().getPath();
                byte[] bytes = ("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"status\":\"running\",\"path\":\"" + path + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(path.endsWith("/run") ? 202 : 200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            ragas.start();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("agente.ragas-service-url", () -> "http://127.0.0.1:" + ragas.getAddress().getPort());
        registry.add("AI_INTERNAL_TOKEN", () -> "test-token");
    }
    @AfterAll static void stop() { ragas.stop(0); }

    @Autowired MockMvc mvc;
    @MockitoBean RecomendacaoRepository repository;

    @Test @WithMockUser(roles = "ADMIN")
    void runsExperimentByRestWithStoredQuestionAnswerAndContexts() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.of(new ObjectMapper().readTree(
                "{\"status\":\"generated\",\"evaluation_input\":{\"question\":\"q\",\"answer\":\"a\",\"contexts\":[\"c1\"]}}")));
        mvc.perform(post("/api/agente/evaluation/experiments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendation_id\":\"" + id + "\",\"reference\":\"ref\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.path").value("/experiments/run"));
        assertTrue(lastBody.get().contains("\"contexts\":[\"c1\"]") && lastBody.get().contains("\"trace_id\":\"" + id + "\""));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void rejectsInvalidIdsAndUnknownRecommendation() throws Exception {
        mvc.perform(get("/api/agente/evaluation/experiments/abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/agente/evaluation/experiments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommendation_id\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/agente/evaluation/experiments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommendation_id\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isNotFound());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void pollsExperimentStatus() throws Exception {
        mvc.perform(get("/api/agente/evaluation/experiments/11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("/experiments/11111111-1111-1111-1111-111111111111"));
    }

    @Test @WithMockUser
    void employeeDenied() throws Exception {
        mvc.perform(get("/api/agente/evaluation/experiments")).andExpect(status().isForbidden());
    }
}
