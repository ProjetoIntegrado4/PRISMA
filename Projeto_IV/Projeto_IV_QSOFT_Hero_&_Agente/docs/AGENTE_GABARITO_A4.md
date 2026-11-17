# Agente de capacitação × gabarito do Exercício A4

Atualizado em 17/11/2026. O agente segue a arquitetura do gabarito (V5.3.2): RAG no Spring Boot com Spring AI e
OpenAI, serviços FastAPI para ML e RAGAS, RabbitMQ para o treino assíncrono e PostgreSQL com pgvector.
As recomendações continuam baseadas nos dados de cada colaborador (CSVs em `dados/csv_preenchidos`).

## Como iniciar

1. No arquivo `.env`, preencha `OPENAI_API_KEY=` com a sua chave (sem aspas). Ela não fica em nenhum código.
2. No PowerShell, na raiz do projeto: `./iniciar-agente.ps1` (na primeira vez reconstrói as imagens, um serviço por vez).
3. Abra http://localhost:3000/agente.html (admin ou gerente). Avaliações RAGAS: http://localhost:3000/avaliacao.html.
4. RabbitMQ Management: http://localhost:15672 (usuário `qai`, senha `AI_RABBIT_PASSWORD` do `.env`).

## Contêineres (item 1 do roteiro)

| Serviço | Papel |
|---|---|
| `postgres` | Banco principal do Prisma |
| `ai-db` (pgvector/pgvector:pg17) | Banco do agente: `course_embeddings` (vector 1536), recomendações, jobs, modelos de ML, experimentos RAGAS |
| `rabbitmq` | Fila `ml.training.requests` (pedidos) e `ml.training.results` (resultados) |
| `backend` | Spring Boot 3.5 + Spring AI 1.0.3 (API e RAG) |
| `ai-service` | FastAPI: perfil do colaborador a partir dos CSVs, previsão ML, producer do treino |
| `ml-worker` | Consumer Python: treina a regressão logística |
| `ragas-service` | FastAPI RAGAS: `/experiments/run` e `/experiments/{id}` |
| `frontend` | Node (BFF) com as páginas `agente.html` e `avaliacao.html` |

## Comunicação síncrona (REST) — item 2

* Navegador → Spring Boot: `/api/agente/options`, `/profile`, `/recommend`, `/predict`, `/index`, `/evaluation/experiments`.
* Spring Boot → FastAPI (`AiServiceClient`): `POST /profile` (perfil e cursos elegíveis), `POST /predict`, `GET /options`.
* Spring Boot → FastAPI RAGAS (`evaluation/web/EvaluationController.java`): `POST /experiments/run` (202) e
  `GET /experiments/{id}`. A avaliação continua em segundo plano; `avaliacao.html` consulta o status a cada 5 s.

## Spring AI e OpenAI — item 3

* `pom.xml`: `spring-ai-bom` 1.0.3 e `spring-ai-starter-model-openai`. Busca vetorial por SQL/JDBC (sem starter de VectorStore).
* `recommendation/application/RecommendCoursesUseCase.java`: perfil (FastAPI) → recuperação semântica (5 mais similares)
  → geração. Nada de nome, CPF, e-mail ou IDs vai para a OpenAI: só evidências observadas e limites.
* `recommendation/infrastructure/PgVectorCourseSearchAdapter.java`: `EmbeddingModel` (text-embedding-3-small) e
  `ORDER BY embedding <=> CAST(? AS vector)` sobre `course_embeddings`.
* `recommendation/infrastructure/OpenAiRecommendationAdapter.java`: `ChatClient` (gpt-4o-mini) escolhe até 3 cursos
  SOMENTE entre os recuperados; o servidor descarta cursos inventados ou repetidos e atribui as evidências.
* A chave vem de `OPENAI_API_KEY` (arquivo `.env`). Sem chave, o Prisma sobe normalmente e o agente avisa.

## Comunicação assíncrona (RabbitMQ) — item 4

* Producer: `ai-service/app.py`, `publish_training()` → `basic_publish(exchange='', routing_key='ml.training.requests',
  body={"job_id","seed"}, delivery_mode=2)`. O Spring Boot só aciona `POST /training/jobs` (202).
* Exchange: padrão (`""`, direct). Binding implícito: a fila recebe pela chave igual ao nome.
* Consumer: `ai-service/worker.py`, `basic_consume(queue=QUEUE, on_message_callback=on_message)`, `prefetch_count=1`.
* ACK manual: `basic_ack` depois de salvar o status e publicar em `ml.training.results`. Treino com erro vira `failed` e também recebe ACK.
* Teste da fila: `docker compose ... stop ml-worker`, clicar em "Treinar modelo de ML", ver `Ready` subir em
  Queues and Streams, depois `docker compose ... start ml-worker`.
* `POST /predict` é síncrono e não passa pela fila.

## ML

`ai-service/ml.py`: regressão logística (iniciante, intermediário, avançado) sobre as notas fundamentos, verificação
e aplicação. Dados: `dados/csv_preenchidos/assessment_labels.csv`, um **dataset de demonstração** com 90 avaliações
(30 por nível), marcado como `dataset_demonstracao_A4` na coluna `reviewer`, não ligado a colaboradores reais.
Substitua por avaliações reais quando existirem.

## RAGAS

`ai-service/evaluation.py`: faithfulness e answer_relevancy sempre; context_precision e context_recall só quando há
resposta de referência. Usa a OpenAI com a mesma chave do `.env`.
