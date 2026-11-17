$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    docker compose --env-file .env -f docker-compose.yml -f compose.ai.yml -f compose.agente-local.yml up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar os serviços.' }
    foreach ($model in @('qwen2.5:1.5b','nomic-embed-text:v1.5')) {
        docker compose --env-file .env -f docker-compose.yml -f compose.ai.yml -f compose.agente-local.yml exec -T ollama ollama pull $model
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar modelo local.' }
    }
    Write-Host 'Abra http://localhost:3000/agente.html'
} finally { Pop-Location }
