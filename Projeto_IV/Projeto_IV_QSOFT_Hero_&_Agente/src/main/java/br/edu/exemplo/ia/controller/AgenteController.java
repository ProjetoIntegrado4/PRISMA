package br.edu.exemplo.ia.controller;

import br.edu.exemplo.ia.recommendation.application.RecommendCoursesUseCase;
import br.edu.exemplo.ia.recommendation.domain.AgenteIndisponivelException;
import br.edu.exemplo.ia.recommendation.domain.PerfilIndisponivelException;
import br.edu.exemplo.ia.recommendation.infrastructure.AiServiceClient;
import br.edu.exemplo.ia.recommendation.infrastructure.OpenAiChave;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Fronteira REST autenticada do agente de capacitação (comunicação síncrona navegador → Spring Boot).
 * A recomendação (RAG com Spring AI + OpenAI) roda aqui no monólito; perfil e ML ficam no FastAPI.
 * A chave da OpenAI e os serviços internos nunca chegam ao navegador.
 */
@RestController
@RequestMapping("/api/agente")
public class AgenteController {
    private final AiServiceClient aiService;
    private final RecommendCoursesUseCase recommendCourses;
    private final OpenAiChave openAi;

    public AgenteController(AiServiceClient aiService, RecommendCoursesUseCase recommendCourses, OpenAiChave openAi) {
        this.aiService = aiService;
        this.recommendCourses = recommendCourses;
        this.openAi = openAi;
    }

    @GetMapping("/options")
    public ResponseEntity<JsonNode> options(@RequestParam(required = false) String company) {
        return aiService.get("/options" + (company == null ? "" : "?company=" + URLEncoder.encode(company, StandardCharsets.UTF_8)));
    }

    /** Saúde do serviço Python + se a chave da OpenAI está configurada (sem expor o valor). */
    @GetMapping("/health")
    public ResponseEntity<JsonNode> health() {
        ResponseEntity<JsonNode> response = aiService.get("/health");
        if (response.getBody() instanceof ObjectNode body) {
            body.put("provider", "openai");
            body.put("model", openAi.modelo());
            body.put("openai_configured", openAi.configurada());
        }
        return response;
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JsonNode> job(@PathVariable UUID id) {
        return aiService.get("/jobs/" + id);
    }

    @PostMapping({"/profile", "/predict"})
    public ResponseEntity<JsonNode> analyze(@RequestBody JsonNode body, jakarta.servlet.http.HttpServletRequest request) {
        String operation = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/'));
        return aiService.post(operation, body);
    }

    /** RAG no monólito: perfil (FastAPI) → busca no pgvector (EmbeddingModel) → ChatClient (até 3 cursos). */
    @PostMapping("/recommend")
    public ResponseEntity<JsonNode> recommend(@RequestBody JsonNode body) {
        try {
            return ResponseEntity.ok(recommendCourses.recommend(body));
        } catch (PerfilIndisponivelException ex) {
            return ResponseEntity.status(ex.status()).body(ex.body());
        } catch (AgenteIndisponivelException ex) {
            return aiService.error(ex.getMessage());
        }
    }

    /** Gera os embeddings dos 12 cursos do catálogo no pgvector (síncrono, só os que mudaram). */
    @PostMapping("/index")
    public ResponseEntity<JsonNode> index() {
        try {
            return ResponseEntity.ok(recommendCourses.indexCatalog());
        } catch (PerfilIndisponivelException ex) {
            return ResponseEntity.status(ex.status()).body(ex.body());
        } catch (AgenteIndisponivelException ex) {
            return aiService.error(ex.getMessage());
        }
    }

    /** Treino assíncrono: Spring Boot aciona o FastAPI por REST (202); o FastAPI publica no RabbitMQ. */
    @PostMapping("/training/jobs")
    public ResponseEntity<JsonNode> train() {
        return aiService.post("/training/jobs", null);
    }
}
