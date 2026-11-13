# EventHub — Script de Inicialização (PowerShell)
# ============================================
# Inicia todos os microsserviços e infraestrutura do EventHub
# Uso: ./start-eventhub.ps1

param(
    [switch]$Build = $false,
    [switch]$NoWait = $false,
    [switch]$Logs = $false
)

# Cores para output
$Colors = @{
    Info    = 'Cyan'
    Success = 'Green'
    Warning = 'Yellow'
    Error   = 'Red'
}

function Write-Log {
    param([string]$Message, [string]$Type = 'Info')
    $Color = $Colors[$Type]
    Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] $Message" -ForegroundColor $Color
}

function Check-Command {
    param([string]$Command)
    $exists = $null -ne (Get-Command $Command -ErrorAction SilentlyContinue)
    return $exists
}

# Validações iniciais
Write-Log "🔍 Verificando pré-requisitos..." Info

if (-not (Check-Command docker)) {
    Write-Log "❌ Docker não encontrado. Instale Docker Desktop em https://www.docker.com/products/docker-desktop" Error
    exit 1
}
Write-Log "✅ Docker disponível" Success

if (-not (Check-Command docker-compose)) {
    # Tenta docker compose (nova sintaxe integrada)
    $composeCmd = "docker compose"
    & docker compose --version *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Log "❌ Docker Compose não encontrado." Error
        exit 1
    }
} else {
    $composeCmd = "docker-compose"
}
Write-Log "✅ Docker Compose disponível" Success

# Verificar se Docker Engine está rodando
Write-Log "🔌 Verificando se Docker Engine está ativo..." Info
$retries = 0
$maxRetries = 5
while ($retries -lt $maxRetries) {
    docker ps *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Log "✅ Docker Engine ativo" Success
        break
    }
    $retries++
    if ($retries -lt $maxRetries) {
        Write-Log "⏳ Docker Engine não está pronto. Tentando novamente em 2s..." Warning
        Start-Sleep -Seconds 2
    }
}

if ($retries -eq $maxRetries) {
    Write-Log "❌ Docker Engine não ficou pronto. Inicie o Docker Desktop manualmente." Error
    exit 1
}

# Verificar se há arquivo docker-compose.yml
if (-not (Test-Path "docker-compose.yml")) {
    Write-Log "❌ Arquivo docker-compose.yml não encontrado no diretório atual." Error
    exit 1
}
Write-Log "✅ docker-compose.yml encontrado" Success

# Limpar containers antigos (opcional)
Write-Log "🧹 Verificando containers existentes..." Info
$runningContainers = & docker ps -q | Measure-Object -Line
if ($runningContainers.Lines -gt 0) {
    Write-Log "⚠️  Há containers em execução. Use 'docker compose down' para parar todos antes." Warning
}

# Build (opcional)
if ($Build) {
    Write-Log "🔨 Construindo imagens (--build)..." Info
    & docker compose build
    if ($LASTEXITCODE -ne 0) {
        Write-Log "❌ Erro ao construir imagens." Error
        exit 1
    }
    Write-Log "✅ Imagens construídas com sucesso" Success
}

# Iniciar containers
Write-Log "🚀 Iniciando containers em background..." Info
& docker compose up -d --build:$Build
if ($LASTEXITCODE -ne 0) {
    Write-Log "❌ Erro ao iniciar containers." Error
    exit 1
}
Write-Log "✅ Containers iniciados" Success

# Aguardar serviços ficarem saudáveis
if (-not $NoWait) {
    Write-Log "⏳ Aguardando serviços ficarem saudáveis..." Info
    $maxWait = 180
    $elapsed = 0
    $interval = 5

    while ($elapsed -lt $maxWait) {
        $healthyCount = & docker compose ps -q | ForEach-Object {
            & docker inspect $_ --format='{{.State.Health.Status}}' 2>$null
        } | Where-Object { $_ -eq 'healthy' } | Measure-Object | Select-Object -ExpandProperty Count

        $totalCount = & docker compose ps -q | Measure-Object | Select-Object -ExpandProperty Count

        if ($null -ne $healthyCount -and $healthyCount -gt 0) {
            Write-Log "  → $healthyCount/$totalCount containers saudáveis..." Info
        }

        if ($healthyCount -eq $totalCount -and $totalCount -gt 0) {
            Write-Log "✅ Todos os serviços estão saudáveis!" Success
            break
        }

        Start-Sleep -Seconds $interval
        $elapsed += $interval

        if ($elapsed % 30 -eq 0) {
            Write-Log "  ⏳ Ainda aguardando ($elapsed/$maxWait segundos)..." Warning
        }
    }

    if ($elapsed -ge $maxWait) {
        Write-Log "⚠️  Timeout ao aguardar serviços (180s). Verifique os logs com: docker compose logs -f" Warning
    }
}

# Exibir status
Write-Log "📊 Status dos containers:" Info
& docker compose ps

# Exibir URLs de acesso
Write-Log "🌐 URLs de Acesso:" Info
Write-Host ""
Write-Host "  Aplicação Web (SPA):           http://localhost:8080" -ForegroundColor Green
Write-Host "  API Gateway + Swagger:         http://localhost:8080/swagger.html" -ForegroundColor Green
Write-Host "  RabbitMQ Management:           http://localhost:15672 (user: eventhub / senha: eventhub_secret_2026)" -ForegroundColor Green
Write-Host "  Healthcheck Gateway:           http://localhost:8080/health" -ForegroundColor Green
Write-Host ""

# Exibir comandos úteis
Write-Log "📝 Comandos Úteis:" Info
Write-Host ""
Write-Host "  Ver logs em tempo real:        docker compose logs -f" -ForegroundColor Cyan
Write-Host "  Ver logs de um serviço:        docker compose logs -f registration-service" -ForegroundColor Cyan
Write-Host "  Parar todos os containers:     docker compose down" -ForegroundColor Cyan
Write-Host "  Parar e remover volumes:       docker compose down -v" -ForegroundColor Cyan
Write-Host "  Reconstruir imagens:           docker compose up -d --build" -ForegroundColor Cyan
Write-Host ""

Write-Log "✨ EventHub iniciado com sucesso!" Success

# Mostrar logs se solicitado
if ($Logs) {
    Write-Log "📺 Exibindo logs em tempo real (Ctrl+C para sair)..." Info
    & docker compose logs -f
}
