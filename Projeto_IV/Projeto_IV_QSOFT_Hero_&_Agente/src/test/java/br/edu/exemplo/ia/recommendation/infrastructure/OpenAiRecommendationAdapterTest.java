package br.edu.exemplo.ia.recommendation.infrastructure;

import br.edu.exemplo.ia.recommendation.domain.AgenteIndisponivelException;
import br.edu.exemplo.ia.recommendation.domain.Course;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiRecommendationAdapterTest {
    final List<Course> retrieved = List.of(
            new Course("CSV01", "Fundamentos", "basico", "c", "k", "conhecer_curso", List.of("role")),
            new Course("CSV02", "Responsável", "basico", "c", "k", "conhecer_curso", List.of("security")),
            new Course("CSV03", "Segurança", "basico", "c", "k", "conhecer_curso", List.of("security")),
            new Course("CSV04", "LGPD", "intermediario", "c", "k", "conhecer_curso", List.of("privacy")));

    OpenAiRecommendationAdapter adapter(String key) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return new OpenAiRecommendationAdapter(builder, new ObjectMapper(), new OpenAiChave(key, "gpt-4o-mini"));
    }

    @Test
    void keepsOnlyRetrievedUniqueAndAtMostThree() {
        var result = adapter("sk-test").validate("""
                ```json
                {"recomendacoes":[{"course_id":"CSV99","explanation":"inventado"},{"course_id":"CSV02","explanation":"a"},
                {"course_id":"CSV02","explanation":"repetido"},{"course_id":"CSV01","explanation":"b"},
                {"course_id":"CSV03","explanation":"c"},{"course_id":"CSV04","explanation":"d"}]}
                ```""", retrieved);
        assertEquals(List.of("CSV02", "CSV01", "CSV03"), result.stream().map(r -> r.course().id()).toList());
        assertEquals(List.of("security"), result.get(0).evidenceIds(), "evidências atribuídas pelo servidor");
    }

    @Test
    void invalidAnswerFailsSafely() {
        assertThrows(AgenteIndisponivelException.class, () -> adapter("sk-test").validate("não é json", retrieved));
        assertThrows(AgenteIndisponivelException.class, () -> adapter("sk-test").validate("{\"recomendacoes\":[]}", retrieved));
    }

    @Test
    void missingKeyIsReportedBeforeCallingOpenAi() {
        var ex = assertThrows(AgenteIndisponivelException.class, () -> adapter("sem-chave-configurada").suggest("p", "", retrieved));
        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
    }

    @Test
    void vectorLiteralIsPgvectorText() {
        assertEquals("[0.5,-1.0]", PgVectorCourseSearchAdapter.vectorLiteral(new float[]{0.5f, -1f}));
    }

    @Test
    void openAiErrorsDoNotLeakDetails() {
        System.out.println("Erro no Open IA recomendation Teste");
        var ex = OpenAiErros.traduzir(new RuntimeException("HTTP 401 - Incorrect API key provided: sk-abc123"));
        assertFalse(ex.getMessage().contains("sk-abc123"));
        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
    }
}
