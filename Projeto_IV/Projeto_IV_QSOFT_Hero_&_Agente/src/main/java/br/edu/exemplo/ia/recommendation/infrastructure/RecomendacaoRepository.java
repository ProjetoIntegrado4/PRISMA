package br.edu.exemplo.ia.recommendation.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Guarda cada recomendação gerada (com pergunta, resposta e contextos) para a avaliação RAGAS. */
@Component
public class RecomendacaoRepository {
    private final VetorDatabase database;
    private final ObjectMapper mapper;

    public RecomendacaoRepository(VetorDatabase database, ObjectMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    public ObjectNode save(ObjectNode result) {
        UUID id = UUID.randomUUID();
        result.put("id", id.toString());
        database.jdbc().update("INSERT INTO spring_recommendations(id, result) VALUES (?, CAST(? AS jsonb))", id, result.toString());
        return result;
    }

    public Optional<JsonNode> find(UUID id) {
        List<String> rows = database.jdbc().queryForList("SELECT result::text FROM spring_recommendations WHERE id = ?", String.class, id);
        if (rows.isEmpty()) return Optional.empty();
        try {
            return Optional.of(mapper.readTree(rows.get(0)));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
