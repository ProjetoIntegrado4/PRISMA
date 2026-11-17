package br.edu.exemplo.ia.recommendation.infrastructure;

import br.edu.exemplo.ia.recommendation.domain.AgenteIndisponivelException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Indica se OPENAI_API_KEY foi configurada no .env. O valor da chave nunca é exposto. */
@Component
public class OpenAiChave {
    static final String SEM_CHAVE = "sem-chave-configurada";
    private final boolean configurada;
    private final String modelo;

    public OpenAiChave(@Value("${spring.ai.openai.api-key:" + SEM_CHAVE + "}") String chave,
                       @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String modelo) {
        this.configurada = chave != null && !chave.isBlank() && !SEM_CHAVE.equals(chave);
        this.modelo = modelo;
    }

    public boolean configurada() {
        return configurada;
    }

    public String modelo() {
        return modelo;
    }

    public void exigir() {
        if (!configurada)
            throw new AgenteIndisponivelException("Configure OPENAI_API_KEY no arquivo .env e reinicie o backend.");
    }
}
