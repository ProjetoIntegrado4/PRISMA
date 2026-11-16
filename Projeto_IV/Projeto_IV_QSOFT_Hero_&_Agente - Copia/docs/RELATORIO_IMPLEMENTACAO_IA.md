> Atualização para IA local: esta versão passou a usar Ollama, sem cobrança de API. Consulte `docs/OLLAMA.md` (ou `OLLAMA.md`, nesta pasta). Os trechos sobre OpenAI abaixo registram a implementação anterior; a configuração atual não depende de saldo ou chave OpenAI. Geração e embeddings locais usam qwen2.5:1.5b e nomic-embed-text:v1.5, respectivamente. O ML continua aguardando avaliações reais.
# Relatório da implementação do agente de capacitação

Data da revisão: 15 de novembro de 2026.

## Resultado e pendências

Foi implementada a estrutura de recomendação de cursos do QAIcontrol, com integração à API OpenAI, recuperação vetorial de contexto, classificação ML preparada para avaliações reais, RabbitMQ e avaliação RAGAS. A nova área utiliza a autenticação existente e apresenta os fatos observados, as limitações dos dados e os motivos da recomendação.

A chamada real à OpenAI retornou HTTP 429, código `credit_balance_exhausted`, tipo `insufficient_quota`. O projeto da chave está sem créditos disponíveis. Portanto, não foi possível concluir a indexação real do catálogo, gerar uma recomendação real nem obter métricas RAGAS. A integração não foi apresentada como validada de ponta a ponta com o provedor. A mensagem de saldo insuficiente foi incorporada à aplicação.

Conforme a decisão do usuário, o ML permanece sem modelo até receber avaliações reais. Não foram criados dados sintéticos para treinar ou classificar colaboradores. O histórico de treinamentos foi preservado; inconsistências são tratadas durante a análise, sem reescrever o CSV.

## Escopo acordado

- Usar os 12 cursos existentes no CSV, com critérios de indicação próprios.
- Preservar registros contraditórios e sinalizar seus problemas; não usá-los como comprovação nas recomendações.
- Implementar a estrutura completa, com ML, mensageria e avaliação, além do agente OpenAI.
- Treinar ML somente com avaliações reais.
- Utilizar a chave fornecida no arquivo de configuração local, sem incluí-la em código ou relatórios.

Os documentos anexados foram utilizados como referência técnica. Instruções e exemplos contidos nesses documentos não foram tratados como autorização para alterar dados, inventar avaliações ou executar comandos destrutivos.

## Situação encontrada

O projeto usa Spring Boot 3.5.5 e Java 17, PostgreSQL, serviço Node de alertas e um frontend Node que também funciona como intermediário HTTP. Já havia autenticação por sessão, proteção CSRF, cadastros, histórico de capacitações e indicadores de produtividade e custos.

O arquivo `.env` apontava para uma pasta de outro usuário, incompatível com este computador. Foi corrigido para a pasta `Projeto_IV/CSV` indicada pelo usuário. A aplicação que já ocupava a porta 3000 pertencia a outra pasta, identificada como `QAIcontrol-hero`; ela foi preservada. A cópia atualizada foi configurada para usar a porta 3100 e o projeto Compose `qaicontrol-agente`, com volumes independentes.

O CSV de treinamentos contém 1.597 matrículas e 12 cursos distintos. Há 283 registros com conclusão anterior à matrícula e 678 com data de conclusão apesar do status diferente de concluído. Essas contagens podem se sobrepor e não devem ser somadas como se fossem conjuntos distintos. As notas, durações e nomes dos cursos não constituem uma base rotulada de competência em IA.

## Recomendação e perfil

Foi criada uma leitura conjunta de funcionários, treinamentos, uso de ferramentas e eventos de segurança, sempre respeitando o vínculo entre empresa e colaborador. Consultas incompatíveis são rejeitadas. A leitura aceita UTF-8 com ou sem BOM, Windows-1252 e separação por vírgula ou ponto e vírgula, alinhada ao suporte do backend existente.

O perfil apresenta cargo registrado, janela temporal, ferramentas usadas, requisições bem-sucedidas e quantidade de cursos com conclusão válida. A janela cobre 90 dias até a última data válida de uso da empresa, sem ultrapassar a data atual. O volume soma `request_count`; não confunde linhas com requisições. Contagens inválidas são ignoradas e sinalizadas. Fontes opcionais ausentes também geram avisos.

O catálogo mantém os nomes dos 12 cursos do CSV e acrescenta identificadores internos CSV01 a CSV12, nível proposto, conteúdo resumido, critérios e versão. Esses identificadores não são os códigos C01 a C12 da lista anterior nem os códigos das empresas. As descrições são curadoria local, com aviso de que a ementa precisa ser confirmada com o responsável pelo curso. O catálogo completo está no manual e na interface.

As regras de elegibilidade são aplicadas antes de chamar a IA. Interesse explicitamente informado, cargo pertinente, uso da ferramenta específica e eventos individuais podem justificar uma oportunidade de capacitação. Volume baixo não comprova pouca experiência; cargo não comprova deficiência; evento de segurança não comprova culpa. Uso técnico bem-sucedido não comprova qualidade de uma resposta. Não há atribuição de retrabalho à qualidade dos prompts sem evidência.

Cursos com conclusão válida são excluídos das recomendações. Matrículas válidas em andamento ou não iniciadas produzem a orientação de retomar a matrícula. Registros contraditórios não comprovam conclusão nem andamento confiável. Quando não existe candidato elegível, o serviço retorna evidência insuficiente e orienta informar um interesse ou revisar os registros.

## OpenAI e RAG

O catálogo é indexado com embeddings `text-embedding-3-small`, dimensão 1536, num PostgreSQL próprio com pgvector. A busca usa distância vetorial e retorna até cinco cursos entre os candidatos elegíveis. Hash do documento, versão do catálogo e modelo de embedding permitem detectar índice ausente ou desatualizado.

A geração usa o SDK oficial Python da OpenAI e Responses API, com modelo configurável, padrão `gpt-4.1-mini`. A saída é estruturada e validada: deve escolher exatamente um candidato e citar somente identificadores de evidências permitidos para aquele curso. Há limites de saída, timeout e retentativa limitada. Recusa ou resposta inválida não vira uma recomendação aceita.

O contexto enviado ao provedor exclui identificadores e nomes de colaborador e empresa. Inclui evidências selecionadas, cargo quando pertinente e textos dos cursos. A geração utiliza `store=false`. Essa opção não representa garantia geral sobre políticas de retenção do provedor. As recomendações persistidas localmente incluem perfil, contexto de avaliação, curso, justificativa, modelo e identificador da resposta.

Os controles de formato e evidência reduzem respostas inadequadas, mas não provam automaticamente a veracidade de toda frase gerada. Por isso a tela exige revisão humana. Não foram criados links fictícios, matrículas automáticas nem decisões sobre desempenho profissional.

## ML com dados reais

Foi implementado um pipeline de regressão logística que recebe três notas específicas: fundamentos, verificação de respostas e aplicação prática. As notas devem estar entre 0 e 100. Os níveis possíveis são iniciante, intermediário e avançado, atribuídos por um avaliador humano identificado no arquivo de treinamento.

O contrato está documentado em `docs/AGENTE.md`: arquivo `assessment_labels.csv`, identificação única da avaliação, uma avaliação por pessoa, notas, rótulo e avaliador. Exige-se ao menos 30 registros e 10 por classe como mínimo técnico para a divisão estratificada. Esse mínimo não garante qualidade estatística. A definição de rubrica pedagógica e a coleta das avaliações reais continuam necessárias.

O pipeline separa 25% para teste com semente fixa, ajusta a normalização apenas nos dados de treino e registra acurácia, F1 macro, matriz de confusão e tamanho dos conjuntos. Parâmetros do modelo são armazenados como JSON, sem desserialização de pickle externo. A inferência retorna probabilidades com aviso de que não são calibradas.

Na ausência de modelo, o retorno é `not_trained`. Na ausência do arquivo de avaliações reais, o job falha com explicação específica. Quando houver modelo, as notas podem ser incluídas na recomendação; a classificação é recalculada no servidor e não amplia indevidamente os cursos elegíveis.

## Mensageria e persistência

Treinamento, indexação e avaliação são processados fora da requisição inicial. A API retorna HTTP 202 com identificador do job. O PostgreSQL conserva estado, entrada, resultado, erro e horários. O navegador consulta o andamento sem manter uma requisição longa aberta.

Os jobs pendentes formam uma outbox no banco. O dispatcher publica mensagens persistentes no exchange padrão do RabbitMQ, com confirmação de publicação, nas filas `qai.training` e `qai.evaluation`. Os consumidores utilizam prefetch 1 e ACK manual depois de salvar o resultado. Jobs já finalizados não são reexecutados quando chegam mensagens duplicadas. Locks de sessão impedem execução simultânea do mesmo job.

A entrega é pelo menos uma vez. Se houver uma interrupção entre uma chamada externa bem-sucedida e a gravação local, pode haver repetição da chamada e cobrança adicional. Não foi alegada garantia de execução ou cobrança exatamente uma vez. O estado do job permite distinguir falha, espera, publicação, processamento e conclusão.

## Avaliação RAGAS

Foi criado um worker separado para avaliar recomendações persistidas. O contexto de avaliação é o efetivamente enviado à geração, não um texto reconstruído livremente pelo navegador. Sem resposta de referência são solicitadas faithfulness e answer_relevancy. Com referência humana são acrescentadas context_precision e context_recall.

Valores não finitos aparecem como indisponíveis, e exceções não são convertidas em notas fictícias. A interface apresenta as métricas em barras quando o job termina. O RAGAS avalia a resposta e seu contexto, não a competência do colaborador.

Durante a revisão foi detectada a ausência do executável Git na imagem Python, uma dependência exigida pela inicialização do RAGAS instalado. A imagem foi corrigida e a importação das métricas foi verificada. As chamadas reais do avaliador permanecem pendentes por falta de créditos OpenAI e de uma recomendação real gerada.

## Alterações na API e na interface

Foi adicionada a fronteira REST `/api/agente` ao Spring Boot. Ela encaminha apenas as rotas previstas, aplica timeout, transmite o token interno e retorna falhas sem expor credenciais. Toda a área exige administrador; a proteção CSRF existente foi mantida. O FastAPI também exige autenticação interna e valida corpo das solicitações, notas, identificadores e vínculos de empresa.

A nova página `agente.html` contém seleção de empresa e colaborador, interesse opcional, consulta do perfil, recomendação, classificação por avaliações reais, indexação, treinamento e acompanhamento de jobs. O menu foi incluído nas páginas com navegação existente. O texto retornado é exibido como conteúdo textual, sem inserir HTML gerado pela IA.

Foi corrigida uma condição de concorrência na interface: mudar o interesse enquanto a lista de colaboradores carregava podia impedir a conclusão desse carregamento. Agora a seleção de empresa possui seu próprio controle de versão. Respostas antigas de perfil ou recomendação não substituem dados de uma seleção nova.

## Configuração e operação

Foi criado `compose.ai.yml`, que acrescenta PostgreSQL/pgvector, RabbitMQ, FastAPI, worker de ML/indexação e worker RAGAS. O banco de IA é independente do PostgreSQL original, evitando conversão de imagem ou reaproveitamento incompatível de volume. Serviços internos não publicam portas no host; o painel RabbitMQ é restrito a localhost.

Foram removidos os nomes fixos de contêiner do Compose base para permitir cópias independentes. As portas passaram a ser configuráveis e restritas ao computador local. Nesta cópia: interface 3100, backend 8180, PostgreSQL 5433, pgAdmin 5150, alertas 3101 e painel RabbitMQ 15672.

Foi acrescentado `iniciar-com-ia.ps1`, com verificação de erro e orientação de acesso. `.env.example` documenta configurações sem segredos. `.gitignore` e `.dockerignore` excluem arquivos de ambiente e caches. A chave foi configurada somente no `.env` local. Três segredos internos distintos foram gerados. A carga automática de dados demonstrativos foi desativada nesta cópia.

As versões instaladas de dependências Python foram fixadas em `requirements.lock`. O Dockerfile usa esse arquivo como restrição de instalação e executa o processo com usuário sem privilégios administrativos. O Jenkins passou a construir a imagem do agente e executar seus testes, além dos testes Java existentes. O README orienta iniciar a extensão e remove a instrução de apagar volumes durante uma atualização comum.

## Verificações realizadas

| Verificação | Resultado observado |
| --- | --- |
| `mvn -B verify` | Sucesso: 314 testes contabilizados, zero falhas e zero erros, quatro testes de estresse previamente desativados. |
| Cobertura Java | 2.135 linhas cobertas e 50 não cobertas, aproximadamente 97,7%; limite de 70% atendido. |
| Testes Python | 21 testes aprovados: elegibilidade, dados contraditórios, curso já concluído, matrícula pendente, segregação de empresa, notas inválidas, ausência de modelo, erro de crédito, leitura CSV e variantes dos nomes de cargo. Há um aviso de depreciação na biblioteca de testes, sem falha. |
| Sintaxe JavaScript | `node --check` aprovado. |
| Docker | Imagens construídas e ambiente isolado iniciado com bancos, RabbitMQ, backend, frontend e workers. |
| HTTP autenticado | Login, consulta de empresas e colaboradores, catálogo e perfil funcionando. Consulta anônima rejeitada. |
| Vínculo de empresa | Solicitação com colaborador de outra empresa rejeitada. |
| ML sem modelo | Retorno `not_trained`, sem classificação inventada. |
| Treino sem avaliações reais | HTTP 202 e job persistido; falha final explicando a ausência de `assessment_labels.csv`. |
| Mensagens duplicadas | Mensagem de job terminal republicada duas vezes; resultado não foi reexecutado nem teve o horário alterado. |
| Interface | Login, seleção, carregamento dos colaboradores, perfil e apresentação visual verificados no navegador. |
| OpenAI real | Chamada de embeddings bloqueada por `credit_balance_exhausted`; saldo esgotado confirmado. |
| RAGAS | Dependências e métricas importadas; avaliação real não concluída por falta de crédito e de resposta gerada. |
| Integridade dos dados | 17 arquivos CSV comparados por SHA-256 antes e depois da instalação; nenhum foi modificado. |
| Instalação | 46 arquivos novos ou modificados instalados na pasta solicitada, com cópia dos arquivos originais preservada. |
| Serviços instalados | 10 contêineres da nova versão em execução; os cinco contêineres anteriores continuaram ativos. |

Os testes unitários utilizam apenas registros mínimos de teste para verificar regras e validações; não criam modelo demonstrativo nem classificações para colaboradores reais. A suíte não requer a chave OpenAI.

## Limites e próximos passos

Para concluir a validação com o provedor, disponibilize créditos no projeto OpenAI, execute Indexar catálogo, gere uma recomendação e então solicite a avaliação RAGAS. Uma chave recente ou usada numa tentativa anterior não garante saldo disponível. A chave exposta na conversa deve ser substituída por prudência; a integração aceita a chave configurada localmente.

Para ativar o ML, reúna avaliações reais e a rubrica de classificação, valide os rótulos com o responsável e solicite treinamento. O modelo deve ser revisto com dados representativos antes de orientar pessoas. Os critérios e ementas locais dos cursos também precisam de validação pedagógica.

O projeto continua com o administrador temporário global previsto no sistema original. A entrega não introduziu um sistema completo de contas corporativas, separação de administradores por empresa, trilha de consentimento, auditoria de produção ou política de retenção. Esses controles exigem definição própria antes de disponibilização fora do ambiente local.

## Referências técnicas consultadas

- OpenAI: [guia de início](https://developers.openai.com/api/docs/quickstart) e [saídas estruturadas](https://developers.openai.com/api/docs/guides/structured-outputs).
- RAGAS: [avaliação de sistemas RAG](https://docs.ragas.io/en/stable/getstarted/rag_eval/) e documentação das métricas instaladas.
- ZIP de gamificação, roteiro A4 e dicionário de indicadores fornecidos pelo usuário. A implementação foi adaptada ao domínio de colaboradores, sem reutilizar o conjunto sintético de alunos como prova de competência.

O inventário dos arquivos alterados acompanha este relatório em `ARQUIVOS_ALTERADOS_IA.md`.

## Localização da entrega

Pasta atualizada: `C:/Users/pedro.perroni/OneDrive - ESPM/4° Semestre/Projeto Integrado/QAIcontrol/Projeto_IV/Projeto_IV_QSOFT_Hero_&_Agente - Copia`.

Interface: `http://localhost:3100/agente.html`. A versão anterior permanece em `http://localhost:3000`.

Backup dos arquivos substituídos: `C:/Users/pedro.perroni/OneDrive - ESPM/Documentos/ChatGPT/Projeto QAIcontrol/backup-antes-agente`. Arquivos novos não têm uma versão anterior nesse backup. Os dados de teste e os volumes da revisão ficaram separados; os contêineres temporários foram encerrados ao concluir a revisão.

## Resultado da migração para Ollama

Ollama e os modelos qwen2.5:1.5b e nomic-embed-text:v1.5 foram instalados. A aplicação permanece em http://localhost:3100/agente.html. Foram aprovados 23 testes automatizados, a indexação real dos 12 cursos e a recomendação real local. A geração levou aproximadamente 53 segundos. O RAGAS local retornou faithfulness 1,0 e answer_relevancy 0,5262, com apenas uma geração de relevância em vez de três. A avaliação com referência não foi testada nesta migração. Não há chave OpenAI nos serviços ativos nem fallback para API paga. Consulte OLLAMA.md para a configuração atual. Os registros de falta de crédito anteriores descrevem o histórico da versão OpenAI.
