# Inicia o Prisma com o agente de IA (Spring AI + OpenAI, FastAPI, RabbitMQ, pgvector e RAGAS).
#   ./iniciar-agente.ps1               usa as imagens que ja existem (rapido)
#   ./iniciar-agente.ps1 -Reconstruir  reconstroi as imagens, uma de cada vez
param([switch]$Reconstruir)
# 'Continue': o docker escreve avisos no stderr; os erros sao conferidos pelo $LASTEXITCODE.
$ErrorActionPreference = 'Continue'
# Mude este texto sempre que o codigo do backend ou do ai-service mudar: o script reconstroi sozinho.
$versaoImagens = '2026-11-17-acesso-modelo'
Push-Location $PSScriptRoot
try {
    $compose = @('--env-file', '.env', '-f', 'docker-compose.yml', '-f', 'compose.ai.yml', '-f', 'compose.agente-local.yml')
    $chave = Select-String -Path .env -Pattern '^OPENAI_API_KEY=.+' -Quiet
    if (-not $chave) { Write-Warning 'OPENAI_API_KEY vazia no .env: o agente sobe, mas nao recomenda nem avalia ate a chave ser preenchida.' }
    $marcador = Join-Path $PSScriptRoot '.versao-imagens-agente'
    $versaoAtual = if (Test-Path $marcador) { (Get-Content $marcador -Raw).Trim() } else { '' }
    $semImagemIA = -not (docker images -q prisma-agente-ia:local)
    if ($Reconstruir -or $semImagemIA -or $versaoAtual -ne $versaoImagens) {
        # Uma imagem por vez, para nao faltar memoria no Docker.
        foreach ($servico in @('ai-service', 'backend', 'frontend')) {
            docker compose @compose build $servico
            if ($LASTEXITCODE -ne 0) { throw "Falha ao construir a imagem de $servico." }
        }
        Set-Content -Path $marcador -Value $versaoImagens
    }
    docker compose @compose up -d --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar os servicos.' }
    Write-Host 'Abra http://localhost:3000/agente.html'
} finally { Pop-Location }
