package br.edu.exemplo.ia.recommendation.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;

class OpenAiErrosTest {

    /** Sobe um servidor HTTP local que responde como a OpenAI e chama o EmbeddingModel real do Spring AI. */
    private String falhaReal(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        try {
            OpenAiApi api = OpenAiApi.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).apiKey("sk-teste-1234567890").build();
            OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(api);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> model.embed(List.of("curso")));
            return OpenAiErros.traduzir("teste", ex).getMessage();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void semCreditoEhIdentificadoPeloCodigoDaOpenAi() throws Exception {
        String msg = falhaReal(429, """
                {"error":{"message":"You exceeded your current quota, please check your plan and billing details.","type":"insufficient_quota","param":null,"code":"insufficient_quota"}}""");
        assertTrue(msg.contains("falta de crédito"), msg);
    }

    @Test
    void limitePorMinutoNaoEhConfundidoComFaltaDeCredito() throws Exception {
        String msg = falhaReal(429, """
                {"error":{"message":"Rate limit reached for text-embedding-3-small","type":"requests","param":null,"code":"rate_limit_exceeded"}}""");
        assertTrue(msg.contains("Limite de chamadas"), msg);
        assertFalse(msg.contains("crédito"), msg);
    }

    @Test
    void projetoSemAcessoAoModeloNaoViraFaltaDeCredito() throws Exception {
        String msg = falhaReal(403, """
                {"error":{"message":"Project `proj_abc123` does not have access to model `text-embedding-3-small`","type":"invalid_request_error","param":null,"code":"model_not_found"}}""");
        assertTrue(msg.contains("não tem acesso ao modelo text-embedding-3-small"), msg);
        assertTrue(msg.contains("Allowed models"), msg);
        assertFalse(msg.contains("crédito"), msg);
    }

    @Test
    void modeloInexistente() throws Exception {
        String msg = falhaReal(404, """
                {"error":{"message":"The model `gpt-4o-minii` does not exist or you do not have access to it.","type":"invalid_request_error","param":null,"code":"model_not_found"}}""");
        assertTrue(msg.contains("indisponível") && msg.contains("gpt-4o-minii"), msg);
    }

    @Test
    void chaveInvalida() throws Exception {
        String msg = falhaReal(401, """
                {"error":{"message":"Incorrect API key provided: sk-abc***xyz.","type":"invalid_request_error","param":null,"code":"invalid_api_key"}}""");
        assertTrue(msg.contains("recusou a chave"), msg);
    }

    @Test
    void mascaraChaveNoLog() {
        String limpo = OpenAiErros.mascarar("Incorrect API key provided: sk-proj-AbCdEf1234567890xyz");
        assertFalse(limpo.contains("AbCdEf"), limpo);
        assertTrue(limpo.contains("sk-***"), limpo);
    }
}
