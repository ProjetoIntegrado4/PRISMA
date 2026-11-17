package br.edu.exemplo.ia.recommendation.domain;

import java.util.List;

/**
 * Curso do catálogo curado (12 cursos de training_records.csv), já com as evidências do colaborador
 * que tornam o curso pertinente. As evidências são atribuídas pelo serviço de perfil, nunca pela IA.
 */
public record Course(String id, String name, String level, String content, String criteria,
                     String action, List<String> evidenceIds) {

    public Course {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    /** Texto do curso que vira embedding (sem dados do colaborador). */
    public String document() {
        return "Curso: " + name + "\nNível: " + level + "\nConteúdo: " + content + "\nCritério: " + criteria;
    }
}
