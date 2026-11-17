package br.edu.exemplo.ia.recommendation;

import br.edu.exemplo.ia.recommendation.application.RecommendCoursesUseCase;
import br.edu.exemplo.ia.recommendation.domain.*;
import br.edu.exemplo.ia.recommendation.infrastructure.AiServiceClient;
import br.edu.exemplo.ia.recommendation.infrastructure.RecomendacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendCoursesUseCaseTest {
    final ObjectMapper mapper = new ObjectMapper();
    AiServiceClient ai;
    RecomendacaoRepository repository;
    final List<String> queries = new ArrayList<>();
    final List<String> profilesSentToLlm = new ArrayList<>();

    final SemanticCourseSearch search = new SemanticCourseSearch() {
        public List<Course> search(String query, List<Course> candidates, int limit) {
            queries.add(query);
            return candidates.stream().limit(limit).toList();
        }
        public int index(List<Course> catalog) { return catalog.size(); }
    };
    final CourseRecommender recommender = new CourseRecommender() {
        public List<RecommendedCourse> suggest(String profile, String interests, List<Course> retrieved) {
            profilesSentToLlm.add(profile);
            return retrieved.stream().limit(3).map(c -> new RecommendedCourse(c, "Pertinente a " + c.name(), c.evidenceIds())).toList();
        }
        public String model() { return "gpt-4o-mini"; }
    };

    @BeforeEach
    void setUp() {
        ai = mock(AiServiceClient.class);
        repository = mock(RecomendacaoRepository.class);
        when(repository.save(any())).thenAnswer(inv -> ((ObjectNode) inv.getArgument(0)).put("id", "00000000-0000-0000-0000-000000000009"));
    }

    JsonNode json(String text) throws Exception { return mapper.readTree(text); }

    String profile(int candidates) {
        StringBuilder c = new StringBuilder();
        for (int i = 1; i <= candidates; i++) {
            if (i > 1) c.append(',');
            c.append("{\"id\":\"CSV0").append(i).append("\",\"name\":\"Curso ").append(i)
             .append("\",\"level\":\"basico\",\"content\":\"c\",\"criteria\":\"k\",\"action\":\"conhecer_curso\",\"evidence_ids\":[\"role\"]}");
        }
        return "{\"company_id\":\"C01\",\"employee_id\":\"C01-E0001\",\"role\":\"Engenheiro\",\"evidence\":[{\"id\":\"role\",\"text\":\"Cargo registrado: Engenheiro\"}],"
                + "\"limitations\":[\"Baixo uso não comprova pouca experiência.\"],\"candidates\":[" + c + "]}";
    }

    @Test
    void recommendsAtMostThreeFromRetrievedAndStoresEvaluationInput() throws Exception {
        when(ai.post(eq("/profile"), any())).thenReturn(ResponseEntity.ok(json(profile(5))));
        var useCase = new RecommendCoursesUseCase(ai, search, recommender, repository, mapper);
        ObjectNode result = useCase.recommend(json("{\"company_id\":\"C01\",\"employee_id\":\"C01-E0001\",\"interest\":\"CSV02\"}"));
        assertEquals("generated", result.path("status").asText());
        assertEquals(3, result.path("recommendations").size());
        assertEquals("openai", result.path("provider").asText());
        assertTrue(result.path("evaluation_input").isMissingNode(), "o navegador não recebe o contexto de avaliação");
        assertTrue(queries.get(0).startsWith("Perfil ") && queries.get(0).contains("Objetivos e interesses: Curso 2"));
        verify(repository).save(argThat(saved -> saved.path("evaluation_input").path("contexts").size() == 6));
    }

    @Test
    void doesNotSendNamesOrIdsToTheModel() throws Exception {
        when(ai.post(eq("/profile"), any())).thenReturn(ResponseEntity.ok(json(profile(2))));
        new RecommendCoursesUseCase(ai, search, recommender, repository, mapper).recommend(json("{}"));
        assertFalse(profilesSentToLlm.get(0).contains("C01"));
    }

    @Test
    void noEligibleCourseReturnsInsufficientEvidenceWithoutCallingOpenAi() throws Exception {
        when(ai.post(eq("/profile"), any())).thenReturn(ResponseEntity.ok(json(profile(0))));
        ObjectNode result = new RecommendCoursesUseCase(ai, search, recommender, repository, mapper).recommend(json("{}"));
        assertEquals("insufficient_evidence", result.path("status").asText());
        assertTrue(queries.isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void profileErrorIsPassedThrough() throws Exception {
        when(ai.post(eq("/profile"), any())).thenReturn(ResponseEntity.status(400).body(json("{\"message\":\"Colaborador inexistente\"}")));
        var ex = assertThrows(PerfilIndisponivelException.class,
                () -> new RecommendCoursesUseCase(ai, search, recommender, repository, mapper).recommend(json("{}")));
        assertEquals(400, ex.status());
    }

    @Test
    void indexesCatalogFromOptions() throws Exception {
        when(ai.get("/options")).thenReturn(ResponseEntity.ok(json(
                "{\"companies\":[],\"courses\":[{\"id\":\"CSV01\",\"name\":\"A\"},{\"id\":\"CSV02\",\"name\":\"B\"}]}")));
        ObjectNode result = new RecommendCoursesUseCase(ai, search, recommender, repository, mapper).indexCatalog();
        assertEquals(2, result.path("catalog_courses").asInt());
    }
}
