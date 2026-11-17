package br.edu.exemplo.ia.recommendation.domain;

import java.util.List;

/** Porta da recuperação semântica (R do RAG). */
public interface SemanticCourseSearch {
    /** Devolve até {@code limit} cursos de {@code candidates}, do mais para o menos similar à consulta. */
    List<Course> search(String query, List<Course> candidates, int limit);

    /** Gera e grava os embeddings dos cursos que ainda não estão indexados ou mudaram. */
    int index(List<Course> catalog);
}
