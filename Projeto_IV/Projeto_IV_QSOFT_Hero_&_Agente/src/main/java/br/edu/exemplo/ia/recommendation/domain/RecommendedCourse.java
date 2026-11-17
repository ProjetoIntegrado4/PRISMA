package br.edu.exemplo.ia.recommendation.domain;

import java.util.List;

/** Um dos até 3 cursos sugeridos, com a justificativa gerada e as evidências que o sustentam. */
public record RecommendedCourse(Course course, String explanation, List<String> evidenceIds) {
}
