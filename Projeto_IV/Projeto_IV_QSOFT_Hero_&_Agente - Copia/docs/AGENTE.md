> Atualização para IA local: esta versão passou a usar Ollama, sem cobrança de API. Consulte `docs/OLLAMA.md` (ou `OLLAMA.md`, nesta pasta). Os trechos sobre OpenAI abaixo registram a implementação anterior; a configuração atual não depende de saldo ou chave OpenAI. Geração e embeddings locais usam qwen2.5:1.5b e nomic-embed-text:v1.5, respectivamente. O ML continua aguardando avaliações reais.
# Agente de capacitação do QAIcontrol

O agente recomenda um dos 12 cursos presentes em `training_records.csv`, com justificativa e evidências. A classificação ML é uma função separada baseada em avaliações específicas, e pode complementar a recomendação quando um modelo real estiver disponível. Não há treinamento automático com dados sintéticos.

## Iniciar

Execute `./iniciar-com-ia.ps1` no PowerShell, na pasta do projeto. O equivalente é:

```powershell
docker compose -f docker-compose.yml -f compose.ai.yml up -d --build
```

Acesse `http://localhost:3100/agente.html` nesta cópia e use o login existente. A porta vem de `QAI_FRONTEND_PORT` no `.env`. A versão anterior na porta 3000 pertence a outra pasta e foi preservada. As operações do agente exigem perfil `ADMIN` e proteção CSRF. O administrador atual é global; não existe segregação por empresa para diferentes administradores no modelo de autenticação original.

Configure `.env`, que não deve ser versionado:

| Variável | Finalidade |
| --- | --- |
| QSOFT_DADOS_DIR | Pasta compartilhada pelos cadastros, painel e agente. O agente monta essa pasta somente para leitura. |
| OPENAI_API_KEY | Chave da API, somente nos serviços internos. Requer crédito disponível no projeto OpenAI. |
| OPENAI_MODEL | Modelo de geração e avaliação, padrão `gpt-4.1-mini`. |
| AI_INTERNAL_TOKEN | Segredo compartilhado entre Java e FastAPI. |
| AI_DB_PASSWORD | Senha do banco exclusivo da IA. Use segredo hexadecimal para compatibilidade com a URL de conexão. |
| AI_RABBIT_PASSWORD | Segredo do RabbitMQ; use caracteres hexadecimais. |
| AI_RABBIT_MANAGEMENT_PORT | Porta local do painel RabbitMQ, padrão 15672. |
| COMPOSE_PROJECT_NAME | Nome isolado dos contêineres e volumes desta cópia, `qaicontrol-agente`. |
| QAI_FRONTEND_PORT / QAI_BACKEND_PORT | Portas locais da interface e backend, 3100 e 8180 nesta cópia. |
| QAI_POSTGRES_PORT / QAI_PGADMIN_PORT / QAI_ALERT_PORT | Portas locais 5433, 5150 e 3101 nesta cópia. |
| QSOFT_DEMONSTRACAO | `false` nesta cópia: evita popular políticas e consumos demonstrativos automaticamente. |

Os três segredos internos devem ser diferentes. Para gerar cada valor no PowerShell 7: `[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`. Não envie a saída para outras pessoas nem coloque a chave em mensagens, scripts versionados ou capturas de tela.

Não execute `docker compose down -v` para atualizar. Esse comando elimina volumes. Use `up -d --build` e mantenha backups antes de migrações. A extensão de IA cria seu próprio PostgreSQL com pgvector; não converte nem substitui o banco original.

## Fluxo de uso

1. Em Preparação e acompanhamento, clique em Indexar catálogo. A operação exige a API OpenAI e cria embeddings dos 12 textos curados. Aguarde `succeeded`.
2. Escolha empresa e colaborador. Se existir interesse declarado pela pessoa, selecione o curso correspondente. Não marque um interesse como se tivesse sido declarado quando isso não aconteceu.
3. Consulte o perfil. A tela mostra cargo, janela de análise, volume observado, conclusão válida de cursos e inconsistências.
4. Solicite uma recomendação. Regras locais limitam os cursos elegíveis; a busca vetorial ordena até cinco candidatos; a OpenAI escolhe um candidato e explica o motivo. Sem evidência, o serviço se abstém.
5. Revise a indicação. A recomendação não efetua matrícula e não toma decisões de desempenho profissional.
6. Opcionalmente avalie a resposta com RAGAS. Sem referência, são calculadas faithfulness e answer_relevancy. Com referência humana adequada, são acrescentadas context_precision e context_recall. As métricas são avaliações do texto e contexto, não avaliações da pessoa.

## Critérios e limitações

O catálogo está em `ai-service/catalog.py`. Os nomes vêm do CSV; conteúdos resumidos e níveis são curadoria local, não ementas verificadas de fornecedores. Não foram inventados links ou instituições. A interface mostra os critérios e a procedência.

| Curso | Critério usado |
| --- | --- |
| Fundamentos de IA Generativa | Interesse explícito em fundamentos. Baixo uso não é prova de pouca experiência. |
| IA Responsavel: Vieses e Limitacoes | Interesse explícito ou evento individual de segurança, como oportunidade preventiva. |
| Seguranca da Informacao e Uso de IA | Interesse explícito ou evento individual de segurança; não atribui culpa. |
| LGPD e Ferramentas de IA | Interesse explícito ou evento individual que mencione dados pessoais, privacidade ou LGPD. |
| Etica e Governanca de Dados em IA | Interesse explícito ou cargo de gestão registrado. |
| Introducao ao ChatGPT Enterprise | Interesse explícito ou uso registrado dessa ferramenta. |
| Microsoft 365 Copilot na Pratica | Interesse explícito ou uso registrado dessa ferramenta. |
| Copilot para Desenvolvedores | Interesse explícito, cargo de desenvolvimento ou uso de GitHub Copilot. |
| Prompt Engineering na Pratica | Interesse explícito; não atribui erros ao prompt. |
| Automatizacao de Tarefas com IA | Interesse explícito; não infere automação útil pelo volume de chamadas. |
| Analise de Dados com IA | Interesse explícito ou cargo relacionado a dados. |
| IA para Times Comerciais | Interesse explícito ou cargo comercial. |

Conclusões válidas excluem o curso das recomendações. Matrículas válidas em andamento ou não iniciadas geram a ação de retomar a matrícula. Registros contraditórios permanecem no CSV e aparecem em avisos, mas não comprovam conclusão nem matrícula pendente confiável. O sistema não deduz incompetência pela inexistência de formação registrada. Notas do histórico de cursos não são usadas como avaliações de competência.

A janela de uso é de 90 dias até a última data válida da empresa, limitada à data atual. São somadas `request_count` das linhas `success`; valores negativos, fracionários ou inválidos são ignorados e sinalizados. A janela é exibida para evitar confundir dados históricos com dados de hoje.

Não são usados ROI estimado, horas supostamente poupadas ou custos em moedas diferentes como prova de competência, desperdício ou ganho individual. Também não se infere dificuldade sem IA, erro factual ou causa de retrabalho sem dados específicos.

## Avaliações reais para ML

O arquivo `assessment_labels.csv` deve ser colocado na pasta indicada por `QSOFT_DADOS_DIR`, quando houver dados reais. Nenhum arquivo com pessoas ou notas fictícias é criado. Cabeçalho:

```csv
assessment_id,subject_id,fundamentos,verificacao,aplicacao,label,reviewer
```

As três notas devem resultar de uma avaliação real, numa escala de 0 a 100. `label` deve ser `iniciante`, `intermediario` ou `avancado`, atribuído pelo avaliador identificado em `reviewer`. `subject_id` pode ser pseudônimo e deve identificar uma única pessoa. Exige-se uma avaliação por pessoa e identificadores únicos para impedir que a mesma pessoa entre no treino e no teste. Defina uma rubrica pedagógica e a origem das notas antes de reunir a base.

O mínimo técnico é 30 avaliações, com pelo menos 10 por nível. Isso permite uma divisão estratificada, mas não garante representatividade ou validade científica. O pipeline separa 25% para teste com semente 42, ajusta o StandardScaler apenas no treino e treina regressão logística. Registra acurácia, F1 macro, matriz de confusão, tamanho dos conjuntos, classes e versão do modelo. Os parâmetros são armazenados como JSON, sem carregar arquivos pickle externos. Probabilidades não são calibradas e não devem ser tratadas como certeza.

Clique em Treinar com avaliações reais para enfileirar o trabalho. Sem o arquivo, o job termina como `failed` com motivo explícito; a consulta de classificação retorna `not_trained`. Esse comportamento é intencional. Não se transforma histórico de uso em rótulo de competência.

As notas informadas na tela podem ser incluídas na recomendação pela opção correspondente. A classificação é recalculada no servidor; o navegador não fornece um nível previamente calculado para ser aceito como verdade. Ela complementa a apresentação do contexto, sem liberar cursos que não atendem aos critérios locais.

## Arquitetura e comunicações

```text
Navegador → BFF Node → API Spring Boot autenticada → FastAPI de IA
                                                   ├─ CSV somente leitura
                                                   ├─ PostgreSQL/pgvector próprio
                                                   ├─ OpenAI embeddings e Responses API
                                                   └─ tabela de jobs (outbox)
Worker ML/indexação → RabbitMQ → treino ou indexação → estado persistido
Worker RAGAS        → RabbitMQ → avaliação OpenAI   → estado persistido
```

Consultas de perfil, inferência ML e recomendação usam HTTP síncrono. Criação de job retorna HTTP 202 e o processamento continua pela fila. A interface consulta o estado a cada três segundos enquanto a aba estiver visível.

O FastAPI persiste o job pendente. O dispatcher do worker publica no exchange padrão do RabbitMQ com routing key `qai.training` ou `qai.evaluation`, publicação persistente e confirmação de entrega. Consumidores usam prefetch 1 e ACK manual após persistir sucesso ou falha. Locks de sessão no PostgreSQL evitam execução concorrente do mesmo job. Jobs terminais não são executados novamente. A entrega é pelo menos uma vez; uma interrupção no intervalo entre um efeito externo e a gravação do resultado pode repetir uma chamada externa. Não há garantia de cobrança exatamente uma vez.

Os processos de ML e RAGAS usam a mesma imagem, mas executam em contêineres e filas separados. A integração OpenAI fica no serviço Python com SDK oficial, em vez de acrescentar Spring AI ao Java. O Java continua sendo a fronteira REST autenticada. Essa é uma adaptação do exercício à arquitetura já existente, não uma cópia integral da aplicação de gamificação.

## Endpoints públicos autenticados

Todos possuem prefixo `/api/agente`:

| Método e rota | Uso |
| --- | --- |
| GET /health | Disponibilidade do serviço interno e presença de configuração OpenAI, não valida saldo. |
| GET /options?company=C01 | Empresas, colaboradores da empresa e catálogo. |
| POST /profile | Perfil observado; corpo com company_id, employee_id, interest opcional e assessment opcional. |
| POST /recommend | Recomendação de um curso; mesmo corpo de profile. |
| POST /predict | Classificação a partir de fundamentos, verificacao e aplicacao. |
| POST /training/jobs | Enfileira treinamento com avaliações reais. |
| POST /index/jobs | Enfileira indexação do catálogo. |
| POST /evaluation/jobs | Enfileira avaliação; recommendation_id e reference opcional. |
| GET /jobs/{uuid} | Estado persistido, resultado ou erro do processamento. |

O serviço Python exige `X-Internal-Token`, não publica porta no host e só é acessado pelo backend. O banco da IA e o RabbitMQ AMQP também não expõem portas; somente o painel RabbitMQ fica em localhost. Nomes e identificadores de colaborador e empresa são removidos do contexto enviado à OpenAI. Cargo, evidências selecionadas e critérios de cursos podem ser enviados; uma referência humana adicionada à avaliação também é enviada ao provedor. A geração usa `store=false`. Isso não altera outras políticas de retenção do provedor.

## Diagnóstico

- `credit_balance_exhausted` ou `insufficient_quota`: conferir crédito e limites do projeto OpenAI. Uma chave recente pode ter saldo indisponível.
- Chave recusada: conferir se a chave foi revogada, digitada incorretamente ou pertence ao projeto esperado.
- Catálogo não indexado: rodar a indexação após resolver os créditos.
- Job pendente: conferir `ml-worker`, `ragas-worker` e RabbitMQ.
- ML sem modelo: adicionar avaliações reais e solicitar treinamento.

```powershell
docker compose -f docker-compose.yml -f compose.ai.yml ps
docker compose -f docker-compose.yml -f compose.ai.yml logs --tail=80 ai-service ml-worker ragas-worker
docker compose -f docker-compose.yml -f compose.ai.yml exec ai-service python check_openai.py
```

O último comando faz uma chamada pequena e real de embeddings. Não imprima `.env` nem a configuração expandida do Compose em capturas ou relatórios.

## Testes

```powershell
mvn -B verify
docker compose -f docker-compose.yml -f compose.ai.yml run --rm --no-deps ai-service python -m pytest -q -p no:cacheprovider
node --check frontend/public/agente.js
```

Os testes automatizados não acessam a OpenAI e não criam modelo sintético para uso na aplicação. Os testes de integração reais dependem de serviços e créditos. Resultados e limitações da verificação desta entrega estão em `RELATORIO_IMPLEMENTACAO_IA.md`.
