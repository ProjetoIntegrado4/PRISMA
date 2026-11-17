package br.edu.exemplo.ia.recommendation.infrastructure;

import br.edu.exemplo.ia.recommendation.domain.AgenteIndisponivelException;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Conexão JDBC com o PostgreSQL + pgvector do agente (contêiner ai-db). Fica separada do banco principal
 * do Prisma e não é exposta como DataSource/JdbcTemplate do Spring, para não substituir os do JPA.
 * A conexão só é aberta no primeiro uso: sem o banco vetorial, o restante do Prisma sobe normalmente.
 */
@Component
public class VetorDatabase {
    private final HikariDataSource dataSource = new HikariDataSource();
    private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    private volatile boolean initialized;

    public VetorDatabase(@Value("${agente.vetor.url:jdbc:postgresql://localhost:5433/qai_ai}") String url,
                         @Value("${agente.vetor.usuario:qai_ai}") String user,
                         @Value("${agente.vetor.senha:}") String password) {
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        dataSource.setPoolName("agente-pgvector");
        dataSource.setMaximumPoolSize(4);
        dataSource.setConnectionTimeout(5000);
    }

    public JdbcTemplate jdbc() {
        if (!initialized) initialize();
        return jdbc;
    }

    private synchronized void initialize() {
        if (initialized) return;
        try {
            jdbc.execute("SELECT pg_advisory_xact_lock(77281904)");
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
            // Embeddings OpenAI text-embedding-3-small: 1536 dimensões.
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS course_embeddings (
                        course_id text PRIMARY KEY, content_hash text NOT NULL, embedding_model text NOT NULL,
                        document text NOT NULL, embedding vector(1536) NOT NULL)""");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS spring_recommendations (
                        id uuid PRIMARY KEY, result jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now())""");
            initialized = true;
        } catch (RuntimeException ex) {
            throw new AgenteIndisponivelException("Banco vetorial do agente (ai-db) indisponível. Inicie com iniciar-agente.ps1.", ex);
        }
    }

    @PreDestroy
    void close() {
        dataSource.close();
    }
}
