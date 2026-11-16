package br.edu.exemplo.ia.estresse;

import org.junit.jupiter.api.Tag;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base dos testes de estresse: simula vários usuários ao mesmo tempo e mede o tempo de resposta.
 * Precisa do sistema no ar (docker compose up -d). Não roda no "mvn test" comum.
 * Para rodar todos:  mvn test "-Dgroups=estresse"
 * Critérios: p95 do tempo de resposta < 800 ms e erros < 1%.
 */
@Tag("estresse") // herdada pelos cenários E1 a E4
abstract class EstresseBase {

    static final String URL = "http://localhost:8080";
    static final String BANCO = "jdbc:postgresql://localhost:5432/iadb";
    static final long PAUSA_MS = 500; // intervalo entre as ações de cada usuário

    /** O que cada usuário faz em cada requisição; devolve o status HTTP. */
    interface Acao {
        int executar(HttpClient usuario, int i) throws Exception;
    }

    final List<Long> tempos = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger erros = new AtomicInteger();

    /** Todos os usuários fazem login e, quando todos estão prontos, começam a repetir a ação ao mesmo tempo. */
    void rodar(int usuarios, int requisicoesPorUsuario, Acao acao) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(usuarios);
        CountDownLatch todosLogados = new CountDownLatch(usuarios);
        for (int u = 0; u < usuarios; u++) {
            pool.submit(() -> usuario(requisicoesPorUsuario, acao, todosLogados));
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
    }

    private void usuario(int requisicoes, Acao acao, CountDownLatch todosLogados) {
        try {
            HttpClient usuario = login();
            todosLogados.countDown();
            todosLogados.await(); // largada: ninguém começa antes de todos estarem logados
            for (int i = 0; i < requisicoes; i++) {
                long inicio = System.currentTimeMillis();
                int status = acao.executar(usuario, i);
                tempos.add(System.currentTimeMillis() - inicio);
                if (status != 200) {
                    erros.incrementAndGet();
                }
                Thread.sleep(PAUSA_MS);
            }
        } catch (Exception e) {
            todosLogados.countDown();
            erros.addAndGet(requisicoes);
        }
    }

    /** Imprime o resultado e verifica os critérios de p95 e de taxa de erro. */
    void verificarDesempenho(String cenario, int total) {
        Collections.sort(tempos);
        long p95 = tempos.isEmpty() ? Long.MAX_VALUE : tempos.get((int) Math.ceil(tempos.size() * 0.95) - 1);
        double erroPct = erros.get() * 100.0 / total;
        System.out.printf("%n%s: %d requisicoes | p95: %d ms | erros: %d (%.2f%%)%n", cenario, total, p95, erros.get(), erroPct);
        assertTrue(p95 < 800, "p95 de " + p95 + " ms passou de 800 ms");
        assertTrue(erroPct < 1, "taxa de erro de " + erroPct + "% passou de 1%");
    }

    // ---------- chamadas ao sistema ----------

    int registrarUso(HttpClient usuario, String empresaId, int i) throws Exception {
        String json = "{\"empresaId\":\"" + empresaId + "\",\"tokens\":" + (1 + i * 200) + ",\"model\":\"gpt-4o\"}";
        return post(usuario, "/api/usages", json).statusCode();
    }

    String criarEmpresa() throws Exception {
        String cnpj = String.valueOf(10_000_000_000_000L + System.nanoTime() % 89_999_999_999_999L);
        String corpo = "{\"name\":\"Empresa Estresse\",\"razaoSocial\":\"Empresa Estresse LTDA\",\"cnpj\":\"" + cnpj + "\","
                + "\"numeroFuncionarios\":50,\"cep\":\"01310100\",\"endereco\":\"Av Paulista 1000\",\"cidade\":\"Sao Paulo\","
                + "\"estado\":\"SP\",\"adminResponsavel\":\"Tester\",\"area\":\"Tecnologia\"}";
        return post(login(), "/api/empresa", corpo).body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    long contarUsos(String empresaId) throws Exception {
        try (var con = DriverManager.getConnection(BANCO, "ia", "ia");
             ResultSet rs = con.createStatement().executeQuery(
                     "select count(*) from ai_usage where empresa_id = '" + empresaId + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Login como no navegador: pega o token CSRF e abre a sessão. */
    HttpClient login() throws Exception {
        HttpClient cliente = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        cliente.send(HttpRequest.newBuilder(URI.create(URL + "/api/auth/csrf")).build(), HttpResponse.BodyHandlers.discarding());
        post(cliente, "/api/auth/login", "{\"usuario\":\"admin@qsoft.com\",\"senha\":\"Qsoft123!\"}");
        return cliente;
    }

    int get(HttpClient cliente, String caminho) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + caminho)).build();
        return cliente.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    HttpResponse<String> post(HttpClient cliente, String caminho, String json) throws Exception {
        String csrf = ((CookieManager) cliente.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + caminho))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return cliente.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
