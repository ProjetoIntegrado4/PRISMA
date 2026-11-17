package br.edu.exemplo.ia.recommendation.domain;

import java.util.List;

/** Porta da geração (G do RAG): escolhe até 3 cursos SOMENTE entre os recuperados. */
public interface CourseRecommender {
    List<RecommendedCourse> suggest(String profile, String interests, List<Course> retrieved);

    String model();
}
