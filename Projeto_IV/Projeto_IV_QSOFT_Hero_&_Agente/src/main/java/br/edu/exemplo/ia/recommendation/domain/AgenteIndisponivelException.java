package br.edu.exemplo.ia.recommendation.domain;

/** Falha de um serviço externo do agente (OpenAI, banco vetorial ou serviço Python), com mensagem segura. */
public class AgenteIndisponivelException extends RuntimeException {
    public AgenteIndisponivelException(String message) {
        super(message);
    }

    public AgenteIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
