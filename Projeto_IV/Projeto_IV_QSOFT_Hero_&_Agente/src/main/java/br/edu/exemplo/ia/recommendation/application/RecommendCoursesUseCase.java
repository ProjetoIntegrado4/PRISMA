package br.edu.exemplo.ia.recommendation.application;

import br.edu.exemplo.ia.recommendation.domain.Course;
import br.edu.exemplo.ia.recommendation.domain.CourseRecommender;
import br.edu.exemplo.ia.recommendation.domain.PerfilIndisponivelException;
import br.edu.exemplo.ia.recommendation.domain.RecommendedCourse;
import br.edu.exemplo.ia.recommendation.domain.SemanticCourseSearch;
import br.edu.exemplo.ia.recommendation.infrastructure.AiServiceClient;
import br.edu.exemplo.ia.recommendation.infrastructure.RecomendacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquestra o RAG da recomendação de cursos:
 * 1) perfil observado do colaborador (serviço Python lê os CSVs da base e devolve evidências e cursos elegíveis);
 * 2) recuperação semântica no pgvector (embeddings OpenAI);
 * 3) geração com o ChatClient, limitada aos cursos recuperados (até 3).
 */
@Service
public class RecommendCoursesUseCase {
    static final String QUESTION = "Quais cursos (até 3) são pertinentes a este perfil observado e por quê?";
    private final AiServiceClient aiService;
    private final SemanticCourseSearch semanticSearch;
    private final CourseRecommender recommendations;
    private final RecomendacaoRepository repository;
    private final ObjectMapper mapper;

    public RecommendCoursesUseCase(AiServiceClient aiService, SemanticCourseSearch semanticSearch,
                                   CourseRecommender recommendations, RecomendacaoRepository repository, ObjectMapper mapper) {
        this.aiService = aiService;
        this.semanticSearch = semanticSearch;
        this.recommendations = recommendations;
        this.repository = repository;
        this.mapper = mapper;
    }

    public ObjectNode recommend(JsonNode request) {
        JsonNode profile = ok(aiService.post("/profile", request));
        List<Course> catalog = courses(profile.path("candidates"));
        ObjectNode result = mapper.createObjectNode();
        result.set("profile", profile);
        result.set("recommendations", mapper.createArrayNode());
        if (catalog.isEmpty()) {
            result.put("status", "insufficient_evidence");
            result.put("message", "Não há curso elegível com os dados disponíveis deste colaborador. "
                    + "Informe um interesse de capacitação ou revise o histórico.");
            return result;
        }
        String profileText = profileText(profile);
        String interests = interest(request, catalog);
        // Recuperação semântica ANTES da geração (RAG). Limita o contexto aos 5 mais similares.
        List<Course> retrieved = semanticSearch.search(
                "Perfil " + profileText + ". Objetivos e interesses: " + interests.strip(), catalog, 5);
        List<RecommendedCourse> suggestions = recommendations.suggest(profileText, interests.strip(), retrieved);

        ArrayNode items = result.putArray("recommendations");
        for (RecommendedCourse s : suggestions) {
            ObjectNode item = items.addObject();
            ObjectNode course = item.putObject("course");
            course.put("id", s.course().id()).put("name", s.course().name()).put("level", s.course().level())
                    .put("content", s.course().content()).put("action", s.course().action());
            item.put("explanation", s.explanation());
            item.set("evidence_ids", mapper.valueToTree(s.evidenceIds()));
        }
        result.put("status", "generated");
        result.put("provider", "openai");
        result.put("model", recommendations.model());
        result.put("review_required", true);
        result.set("retrieved", mapper.valueToTree(retrieved.stream().map(Course::id).toList()));
        ObjectNode evaluation = result.putObject("evaluation_input");
        evaluation.put("question", QUESTION);
        evaluation.put("answer", suggestions.stream()
                .map(s -> s.course().name() + ": " + s.explanation()).collect(Collectors.joining("\n")));
        ArrayNode contexts = evaluation.putArray("contexts");
        contexts.add("Perfil observado: " + profileText);
        retrieved.forEach(c -> contexts.add(c.document()));
        ObjectNode saved = repository.save(result);
        ObjectNode response = saved.deepCopy();
        response.remove("evaluation_input");
        return response;
    }

    /** Indexa (embeddings) os cursos do catálogo que ainda não estão no pgvector. */
    public ObjectNode indexCatalog() {
        JsonNode options = ok(aiService.get("/options"));
        List<Course> catalog = courses(options.path("courses"));
        int indexed = semanticSearch.index(catalog);
        ObjectNode result = mapper.createObjectNode();
        result.put("status", "succeeded");
        result.put("catalog_courses", catalog.size());
        result.put("newly_indexed", indexed);
        return result;
    }

    private JsonNode ok(ResponseEntity<JsonNode> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
            throw new PerfilIndisponivelException(response.getStatusCode().value(), response.getBody());
        return response.getBody();
    }

    static List<Course> courses(JsonNode array) {
        List<Course> list = new ArrayList<>();
        for (JsonNode c : array) {
            List<String> evidence = new ArrayList<>();
            c.path("evidence_ids").forEach(e -> evidence.add(e.asText()));
            list.add(new Course(c.path("id").asText(), c.path("name").asText(), c.path("level").asText(),
                    c.path("content").asText(), c.path("criteria").asText(), c.path("action").asText("conhecer_curso"), evidence));
        }
        return list;
    }

    /** Só fatos observados e limites; sem nome, CPF, e-mail ou IDs do colaborador e da empresa. */
    static String profileText(JsonNode profile) {
        StringBuilder text = new StringBuilder();
        profile.path("evidence").forEach(e -> text.append("- ").append(e.path("text").asText()).append('\n'));
        JsonNode assessment = profile.path("assessment");
        if ("predicted".equals(assessment.path("status").asText()))
            text.append("- Classificação ML (estimativa pelas notas informadas): ").append(assessment.path("profile").asText()).append('\n');
        text.append("Limites: ");
        profile.path("limitations").forEach(l -> text.append(l.asText()).append(' '));
        return text.toString().strip();
    }

    private static String interest(JsonNode request, List<Course> catalog) {
        String id = request.path("interest").asText("");
        Set<String> ids = Set.of(id);
        return catalog.stream().filter(c -> ids.contains(c.id())).map(Course::name).findFirst().orElse("");
    }
}
