# IA local com Ollama

Esta configuração substitui o uso pago da OpenAI. Geração, embeddings e avaliação RAGAS usam o Ollama no próprio ambiente Docker. Não há fallback para a nuvem e a chave OpenAI foi retirada da configuração ativa dos serviços.

## Iniciar

Execute `./iniciar-com-ia.ps1` na pasta do projeto. O script inicia os serviços e baixa `qwen2.5:1.5b` e `nomic-embed-text:v1.5`. O download inicial exige internet; os arquivos ficam no volume `ollama_data` e são reaproveitados nos próximos acessos. Não apague os volumes ao atualizar.

O computador tem 8 GB de RAM e o Docker aproximadamente 4 GB disponíveis. Foi escolhido um modelo compacto de 1,5 bilhão de parâmetros, com contexto limitado a 4096 tokens, processamento sequencial e apenas um modelo carregado por vez. O modelo é descarregado após geração e indexação. O processamento por CPU pode demorar e a qualidade deve ser revisada por uma pessoa.

Abra `http://localhost:3100/agente.html`. Clique em Indexar catálogo, aguarde o sucesso e gere uma recomendação. Os erros antigos de saldo ficam no histórico; eles pertencem às tentativas anteriores com OpenAI.

## Implementação

- Serviço `ollama` sem porta pública, na rede privada do Compose; `OLLAMA_NO_CLOUD=1`.
- Geração via `/api/chat`, com esquema JSON e validação de curso e evidências.
- Embeddings locais de 768 dimensões, em tabela própria `ai_courses_local`; não mistura vetores com os antigos embeddings OpenAI de 1536 dimensões.
- RAGAS aponta explicitamente para `http://ollama:11434/v1`, tanto para geração quanto para embeddings. Não usa credenciais OpenAI.
- O ML continua exigindo avaliações reais e não recebe dados sintéticos.
- A chave antiga não é necessária para executar a aplicação. O backup anterior pode conter configurações históricas e deve ser tratado como privado.

## Avaliação

O modelo compacto pode produzir JSON inválido ou avaliações inconsistentes em tarefas complexas. Erros são reportados como falha ou métrica indisponível, sem fabricar resultados. A avaliação RAGAS com o mesmo modelo gerador não é uma validação independente da qualidade. A aplicação continua exigindo revisão humana.

Para ver os modelos e os serviços:

```powershell
docker compose -f docker-compose.yml -f compose.ai.yml exec ollama ollama list
docker compose -f docker-compose.yml -f compose.ai.yml ps
```

## Referências

[Ollama no Docker](https://docs.ollama.com/docker), [compatibilidade de API](https://docs.ollama.com/api/openai-compatibility) e [modelo de embeddings](https://ollama.com/library/nomic-embed-text).

## Testes da adaptação

23 testes automatizados aprovados, incluindo verificação de que a geração aponta para o endereço interno do Ollama e validação de dimensão dos embeddings. A revisão Java anterior continua válida porque a adaptação não alterou o Java.

A indexação real dos 12 cursos foi concluída. A geração real local retornou o curso Fundamentos de IA Generativa para interesse explícito nesse curso, com evidência vinculada pelo servidor. A chamada ao modelo levou aproximadamente 53 segundos nesta máquina. A consulta autenticada de empresas, colaboradores e perfil também passou. A qualidade da redação do modelo compacto exige revisão humana.

A avaliação RAGAS real local também terminou: faithfulness = 1,0 e answer_relevancy = 0,5262, sem resposta de referência. O adaptador recebeu uma geração em vez das três solicitadas para relevância e prosseguiu com uma, reduzindo a robustez dessa medida. Esses valores são de uma única execução e não comprovam qualidade geral. O tempo foi de alguns minutos por CPU. As métricas context_precision e context_recall continuam condicionadas a uma referência humana e não foram validadas nesta migração.
