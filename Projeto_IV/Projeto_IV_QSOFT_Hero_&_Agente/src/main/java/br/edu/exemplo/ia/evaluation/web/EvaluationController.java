package br.edu.exemplo.ia.evaluation.web;

import br.edu.exemplo.ia.recommendation.domain.AgenteIndisponivelException;
import br.edu.exemplo.ia.recommendation.infrastructure.AiServiceClient;
import br.edu.exemplo.ia.recommendation.infrastructure.RecomendacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Avaliação RAGAS por REST: o Spring Boot envia POST /experiments/run ao FastAPI RAGAS e consulta
 * GET /experiments/{id}. A chamada HTTP é síncrona, mas a avaliação prossegue em segundo plano no FastAPI;
 * a página avaliacao.html consulta o status periodicamente.
 */
@RestController
@RequestMapping("/api/agente/evaluation")
public class EvaluationController {
    private final AiServiceClient client;
    private final RecomendacaoRepository recommendations;
    private final ObjectMapper mapper;
    private final String serviceUrl;

    public EvaluationController(AiServiceClient client, RecomendacaoRepository recommendations, ObjectMapper mapper,
                                @Value("${agente.ragas-service-url:http://localhost:8001}") String serviceUrl) {
        this.client = client;
        this.recommendations = recommendations;
        this.mapper = mapper;
        this.serviceUrl = serviceUrl;
    }

    /** Corpo: {"recommendation_id": "...", "reference": "resposta de referência opcional"}. */
    @PostMapping("/experiments")
    public ResponseEntity<JsonNode> run(@RequestBody JsonNode body) {
        UUID id;
        try {
            id = UUID.fromString(body.path("recommendation_id").asText(""));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(mapper.valueToTree(Map.of("message", "ID de recomendação inválido")));
        }
        Optional<JsonNode> record;
        try {
            record = recommendations.find(id);
        } catch (AgenteIndisponivelException ex) {
            return client.error(ex.getMessage());
        }
        if (record.isEmpty() || !"generated".equals(record.get().path("status").asText()))
            return ResponseEntity.status(404).body(mapper.valueToTree(Map.of("message", "Recomendação gerada não encontrada")));
        JsonNode input = record.get().path("evaluation_input");
        String reference = body.path("reference").asText("");
        if (reference.length() > 6000) reference = reference.substring(0, 6000);
        ObjectNode request = mapper.createObjectNode();
        request.put("question", input.path("question").asText());
        request.put("answer", input.path("answer").asText());
        request.set("contexts", input.path("contexts"));
        request.put("reference", reference.strip());
        request.put("trace_id", id.toString());
        return client.call(serviceUrl, "POST", "/experiments/run", request);
    }

    @GetMapping("/experiments/{id}")
    public ResponseEntity<JsonNode> experiment(@PathVariable String id) {
        if (!id.matches("[0-9a-fA-F-]{36}"))
            return ResponseEntity.badRequest().body(mapper.valueToTree(Map.of("message", "ID inválido")));
        return client.call(serviceUrl, "GET", "/experiments/" + id, null);
    }

    @GetMapping("/experiments")
    public ResponseEntity<JsonNode> experiments() {
        return client.call(serviceUrl, "GET", "/experiments", null);
    }
}
