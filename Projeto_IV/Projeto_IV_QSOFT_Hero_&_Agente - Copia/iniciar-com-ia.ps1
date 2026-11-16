$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Crie .env a partir de .env.example e configure os caminhos e segredos locais.' }
    docker compose -f docker-compose.yml -f compose.ai.yml up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível iniciar todos os serviços. Confira a mensagem do Docker acima.' }
    docker compose -f docker-compose.yml -f compose.ai.yml exec -T ollama ollama pull qwen2.5:1.5b
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao baixar o modelo local de texto.' }
    docker compose -f docker-compose.yml -f compose.ai.yml exec -T ollama ollama pull nomic-embed-text:v1.5
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao baixar o modelo de embeddings.' }
    $portLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^QAI_FRONTEND_PORT=' } | Select-Object -Last 1
    $frontendPort = if ($portLine) { ($portLine -split '=', 2)[1].Trim() } else { '3000' }
    Write-Host "Acesse http://localhost:$frontendPort/agente.html. Não use down -v: isso apaga os volumes."
} finally { Pop-Location }
