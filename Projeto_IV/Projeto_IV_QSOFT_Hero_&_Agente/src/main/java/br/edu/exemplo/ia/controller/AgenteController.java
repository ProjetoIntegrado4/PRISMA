package br.edu.exemplo.ia.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Authenticated REST boundary. OpenAI keys and internal services never reach the browser. */
@RestController
@RequestMapping("/api/agente")
public class AgenteController {
    private final String baseUrl;
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AgenteController(@Value("${AI_SERVICE_URL:http://localhost:8000}") String baseUrl,
                            @Value("${AI_INTERNAL_TOKEN:}") String token, ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.mapper = mapper;
    }

    @GetMapping("/options")
    public ResponseEntity<JsonNode> options(@RequestParam(required = false) String company) {
        return call("GET", "/options" + (company == null ? "" : "?company=" + URLEncoder.encode(company, StandardCharsets.UTF_8)), null);
    }

    @GetMapping("/health")
    public ResponseEntity<JsonNode> health() { return call("GET", "/health", null); }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JsonNode> job(@PathVariable UUID id) { return call("GET", "/jobs/" + id, null); }

    @PostMapping({"/profile", "/recommend", "/predict"})
    public ResponseEntity<JsonNode> analyze(@RequestBody JsonNode body, jakarta.servlet.http.HttpServletRequest request) {
        String operation = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/'));
        return call("POST", operation, body);
    }

    @PostMapping({"/training/jobs", "/index/jobs", "/evaluation/jobs"})
    public ResponseEntity<JsonNode> createJob(@RequestBody(required = false) JsonNode body,
                                             jakarta.servlet.http.HttpServletRequest request) {
        String operation = request.getRequestURI().substring(request.getRequestURI().indexOf("/api/agente") + "/api/agente".length());
        return call("POST", operation, body == null ? mapper.createObjectNode() : body);
    }

    private ResponseEntity<JsonNode> call(String method, String path, JsonNode body) {
        if (token.isBlank()) return error("Configure AI_INTERNAL_TOKEN no servidor.");
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(150)).header("X-Internal-Token", token).header("Accept", "application/json");
            if (method.equals("POST")) request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
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

    private ResponseEntity<JsonNode> error(String message) {
        return ResponseEntity.status(503).body(mapper.valueToTree(Map.of("message", message)));
    }
}
