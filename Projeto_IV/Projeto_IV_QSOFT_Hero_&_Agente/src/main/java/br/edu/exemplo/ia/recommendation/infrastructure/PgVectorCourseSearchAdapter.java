package br.edu.exemplo.ia.recommendation.infrastructure;

import br.edu.exemplo.ia.recommendation.domain.Course;
import br.edu.exemplo.ia.recommendation.domain.SemanticCourseSearch;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recuperação semântica com Spring AI (EmbeddingModel da OpenAI) e consulta SQL no pgvector
 * (operador de distância de cosseno {@code <=>}) sobre a tabela course_embeddings.
 */
@Component
public class PgVectorCourseSearchAdapter implements SemanticCourseSearch {
    private final EmbeddingModel embeddingModel;
    private final VetorDatabase database;
    private final OpenAiChave chave;
    private final String model;

    public PgVectorCourseSearchAdapter(EmbeddingModel embeddingModel, VetorDatabase database, OpenAiChave chave,
                                       @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String model) {
        this.embeddingModel = embeddingModel;
        this.database = database;
        this.chave = chave;
        this.model = model;
    }

    @Override
    public int index(List<Course> catalog) {
        chave.exigir();
        Map<String, String> indexed = database.jdbc().query(
                        "SELECT course_id, content_hash, embedding_model FROM course_embeddings",
                        (rs, i) -> Map.entry(rs.getString(1), rs.getString(2) + "|" + rs.getString(3)))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<Course> pending = catalog.stream()
                .filter(c -> !(digest(c.document()) + "|" + model).equals(indexed.get(c.id()))).toList();
        if (pending.isEmpty()) return 0;
        List<float[]> vectors;
        try {
            vectors = embeddingModel.embed(pending.stream().map(Course::document).toList());
        } catch (RuntimeException ex) {
            throw OpenAiErros.traduzir("embeddings dos cursos (indexação)", ex);
        }
        for (int i = 0; i < pending.size(); i++) {
            Course c = pending.get(i);
            database.jdbc().update("""
                            INSERT INTO course_embeddings(course_id, content_hash, embedding_model, document, embedding)
                            VALUES (?, ?, ?, ?, CAST(? AS vector))
                            ON CONFLICT (course_id) DO UPDATE SET content_hash = excluded.content_hash,
                                embedding_model = excluded.embedding_model, document = excluded.document,
                                embedding = excluded.embedding""",
                    c.id(), digest(c.document()), model, c.document(), vectorLiteral(vectors.get(i)));
        }
        return pending.size();
    }

    @Override
    public List<Course> search(String query, List<Course> candidates, int limit) {
        if (candidates.isEmpty()) return List.of();
        index(candidates); // indexa sob demanda os cursos que ainda não têm embedding
        float[] embedding;
        try {
            embedding = embeddingModel.embed(query);
        } catch (RuntimeException ex) {
            throw OpenAiErros.traduzir("embedding da consulta", ex);
        }
        String placeholders = String.join(",", candidates.stream().map(c -> "?").toList());
        List<Object> args = new ArrayList<>(candidates.stream().map(Course::id).toList());
        args.add(model);
        args.add(vectorLiteral(embedding));
        args.add(limit);
        List<String> ids = database.jdbc().queryForList("""
                SELECT course_id FROM course_embeddings
                WHERE course_id IN (%s) AND embedding_model = ?
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?""".formatted(placeholders), String.class, args.toArray());
        Map<String, Course> byId = candidates.stream().collect(Collectors.toMap(Course::id, Function.identity(), (a, b) -> a));
        return ids.stream().map(byId::get).toList();
    }

    static String vectorLiteral(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) text.append(',');
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }

    static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
