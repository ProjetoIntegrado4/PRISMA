package br.edu.exemplo.ia.recommendation.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Comunicação síncrona (REST) do Spring Boot com os serviços FastAPI do agente.
 * O token interno e os endereços dos serviços nunca chegam ao navegador.
 */
@Component
public class AiServiceClient {
    private final String baseUrl;
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AiServiceClient(@Value("${agente.ai-service-url:http://localhost:8000}") String baseUrl,
                           @Value("${agente.internal-token:}") String token, ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
        this.mapper = mapper;
    }

    public ResponseEntity<JsonNode> get(String path) {
        return call(baseUrl, "GET", path, null);
    }

    public ResponseEntity<JsonNode> post(String path, JsonNode body) {
        return call(baseUrl, "POST", path, body);
    }

    /** Chamada a outro serviço FastAPI que usa o mesmo token interno (ex.: RAGAS). */
    public ResponseEntity<JsonNode> call(String serviceUrl, String method, String path, JsonNode body) {
        if (token.isBlank()) return error("Configure AI_INTERNAL_TOKEN no servidor.");
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(serviceUrl.replaceAll("/+$", "") + path))
                    .timeout(Duration.ofSeconds(150)).header("X-Internal-Token", token).header("Accept", "application/json");
            if (method.equals("POST")) request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body == null ? mapper.createObjectNode() : body)));
            else request.GET();
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(mapper.readTree(response.body()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return error("Solicitação interrompida. Tente novamente.");
        } catch (Exception ex) {
            return error("Serviço de IA indisponível. Verifique sua configuração e tente novamente.");
        }
    }

    public ResponseEntity<JsonNode> error(String message) {
        return ResponseEntity.status(503).body(mapper.valueToTree(Map.of("message", message)));
    }
}
