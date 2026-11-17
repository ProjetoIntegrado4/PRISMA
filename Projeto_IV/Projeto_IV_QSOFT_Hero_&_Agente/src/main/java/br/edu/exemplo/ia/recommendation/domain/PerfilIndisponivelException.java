package br.edu.exemplo.ia.recommendation.domain;

import com.fasterxml.jackson.databind.JsonNode;

/** O serviço de perfil (FastAPI) recusou ou não respondeu: a resposta dele é repassada como está. */
public class PerfilIndisponivelException extends RuntimeException {
    private final int status;
    private final transient JsonNode body;

    public PerfilIndisponivelException(int status, JsonNode body) {
        super("Perfil indisponível (" + status + ")");
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public JsonNode body() {
        return body;
    }
}
