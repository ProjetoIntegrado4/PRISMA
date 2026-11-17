package br.edu.exemplo.ia.recommendation.infrastructure;

import br.edu.exemplo.ia.recommendation.domain.AgenteIndisponivelException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduz falhas da OpenAI em mensagens seguras para a tela e registra no log do backend
 * o status, o código e a mensagem originais da OpenAI (sem chave, prompt ou documentos).
 */
final class OpenAiErros {
    private static final Logger log = LoggerFactory.getLogger(OpenAiErros.class);
    private static final Pattern STATUS = Pattern.compile("\\b([45]\\d\\d)\\b");
    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern MODELO = Pattern.compile("model `?([A-Za-z0-9._:\\-]+)`?");
    private static final Pattern CHAVE = Pattern.compile("sk-[A-Za-z0-9_*\\-]{4,}");

    private OpenAiErros() {
    }

    static AgenteIndisponivelException traduzir(RuntimeException ex) {
        return traduzir("chamada à OpenAI", ex);
    }

    static AgenteIndisponivelException traduzir(String etapa, RuntimeException ex) {
        if (ex instanceof AgenteIndisponivelException agente) return agente;
        String original = textoCompleto(ex);
        String text = original.toLowerCase();
        log.warn("Falha da OpenAI em {}: status={} code={} type={} excecao={} mensagem=\"{}\"", etapa,
                achar(STATUS, original), achar(CODE, original), achar(TYPE, original),
                ex.getClass().getSimpleName(), mascarar(achar(MESSAGE, original).equals("-") ? original : achar(MESSAGE, original)));
        if (text.contains("insufficient_quota"))
            return new AgenteIndisponivelException("A OpenAI recusou por falta de crédito (insufficient_quota). A API da OpenAI é cobrada à parte do ChatGPT Plus: "
                    + "adicione crédito em platform.openai.com > Settings > Billing (ou aumente o limite de orçamento do projeto da chave) e tente de novo.", ex);
        if (text.contains("invalid_api_key") || text.contains("incorrect api key") || "401".equals(achar(STATUS, original)))
            return new AgenteIndisponivelException("A OpenAI recusou a chave. Confira OPENAI_API_KEY no arquivo .env.", ex);
        if (text.contains("does not have access to model")) {
            String modelo = achar(MODELO, original);
            return new AgenteIndisponivelException("O projeto da chave OpenAI não tem acesso ao modelo " + modelo
                    + ". Libere-o em platform.openai.com > Settings > Projects > (seu projeto) > Limits > Allowed models, ou use uma chave de outro projeto.", ex);
        }
        if (text.contains("model_not_found") || text.contains("does not exist") || "404".equals(achar(STATUS, original)))
            return new AgenteIndisponivelException("Modelo OpenAI indisponível para esta chave (" + achar(MODELO, original)
                    + "). Confira OPENAI_MODEL e o modelo de embedding.", ex);
        if (text.contains("rate_limit") || "429".equals(achar(STATUS, original)))
            return new AgenteIndisponivelException("Limite de chamadas por minuto da OpenAI atingido. Aguarde alguns segundos e tente novamente.", ex);
        if ("403".equals(achar(STATUS, original)))
            return new AgenteIndisponivelException("A chave da OpenAI não tem permissão para este modelo ou projeto. Confira as permissões da chave.", ex);
        return new AgenteIndisponivelException("Não foi possível concluir a chamada à OpenAI. Tente novamente; o motivo está no log do backend.", ex);
    }

    /** Junta as mensagens da exceção e das causas, onde o Spring AI guarda o corpo da resposta da OpenAI. */
    private static String textoCompleto(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null && sb.length() < 4000; t = t.getCause() == t ? null : t.getCause())
            sb.append(t.getMessage()).append(' ');
        return sb.toString();
    }

    private static String achar(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : "-";
    }

    static String mascarar(String text) {
        String limpo = CHAVE.matcher(String.valueOf(text)).replaceAll("sk-***").replaceAll("\\s+", " ").trim();
        return limpo.length() > 400 ? limpo.substring(0, 400) + "..." : limpo;
    }
}
