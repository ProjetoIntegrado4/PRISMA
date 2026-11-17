# Agente local integrado ao Prisma

Instalação direta autorizada em 16 de novembro de 2026. O projeto usa Ollama local, com os mesmos 12 cursos e critérios. O ML permanece sem modelo até receber avaliações reais.

## Acesso e inicialização

Abra http://localhost:3000/agente.html e use sua conta de administrador ou gerente.

Para iniciar novamente, abra PowerShell na raiz do projeto e execute:

    ./iniciar-agente.ps1

O script carrega docker-compose.yml, compose.ai.yml e compose.agente-local.yml juntos. Não execute apenas compose.ai.yml. O .env já foi configurado com os segredos internos e a base B.

Base B: dados/csv_preenchidos dentro deste projeto. O agente lê a base somente para consulta. Não é necessária chave OpenAI e não há fallback para API paga.

## Arquivos e proteção dos dados

Antes das alterações, os arquivos existentes foram copiados para a pasta backup_antes_agente_20261116_202729. Essa pasta contém também a lista de arquivos alterados e os hashes dos CSVs. O backup inclui configuração privada e não deve ser compartilhado.

Foram adicionados a API Java do agente, o serviço Python, catálogo, regras, workers, integração Ollama, interface, testes e configurações adicionais do Compose. Foram alterados os menus, a proteção de acesso e o .env. O Compose base, pom.xml e as demais permissões do Prisma foram preservados.

compose.agente-local.yml utiliza nomes de contêiner distintos para preservar contêineres antigos. Os volumes originais não foram excluídos. O volume qaicontrol-agente_ollama_data é reutilizado somente como cache local dos modelos existentes. Nenhum arquivo CSV foi corrigido ou apagado.

## Testes

Java: 332 testes contabilizados, zero falhas, zero erros, quatro testes de estresse previamente desativados. O Maven retornou sucesso; o JaCoCo não encontrou dados de execução nesta revisão, por isso não é declarado percentual de cobertura.

Python: 23 testes aprovados na imagem final, com um aviso de depreciação da biblioteca de testes. Sintaxe JavaScript aprovada.

## Uso

Selecione empresa e colaborador, consulte o perfil e solicite uma recomendação. Interesse deve ser informado somente se declarado pelo colaborador. A classificação por notas é opcional; mantenha a inclusão de notas desmarcada enquanto não houver avaliações e modelo treinado.

Indexar catálogo prepara a busca dos cursos. Treinar com avaliações reais exige assessment_labels.csv e não treina o Ollama. A avaliação RAGAS mede a resposta e seu contexto, não a competência da pessoa. Um modelo compacto pode levar cerca de um minuto ou mais para gerar uma resposta e requer revisão humana.

## Diagnóstico

    docker compose --env-file .env -f docker-compose.yml -f compose.ai.yml -f compose.agente-local.yml ps

Não use down -v: esse comando apaga volumes. O script de inicialização não executa esse comando.
## Verificação final da instalação

Serviços iniciados na pasta final autorizada e porta 3000. O teste autenticado confirmou 19 empresas na base B, os 12 cursos do catálogo, consulta do perfil, rejeição de colaborador de outra empresa e retorno not_trained sem modelo ML.

A indexação real dos 12 cursos terminou como succeeded. Uma recomendação real foi gerada pelo Ollama: Fundamentos de IA Generativa para interesse explícito nesse curso. A justificativa gerada continua sujeita a revisão humana.

Os 37 CSVs verificados após a inicialização permanecem idênticos aos hashes anteriores. O backup foi preservado. Os 11 contêineres da aplicação, incluindo Ollama e os dois workers, estavam em execução na conferência final; bancos, RabbitMQ e API de IA saudáveis.

O worker RAGAS foi iniciado, mas as métricas não foram recalculadas nesta instalação. A avaliação pode ser solicitada opcionalmente pela interface após gerar uma recomendação.

A URL final é http://localhost:3000/agente.html. Não há cobrança de API, chave OpenAI injetada nos serviços de IA ou necessidade de preencher notas para recomendar cursos.