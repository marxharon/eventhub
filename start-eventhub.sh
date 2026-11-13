#!/bin/bash
# EventHub — Script de Inicialização (Bash)
# ==========================================
# Inicia todos os microsserviços e infraestrutura do EventHub
# Uso: ./start-eventhub.sh [opções]

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Funções
log_info() {
    echo -e "${CYAN}[$(date +'%H:%M:%S')] $1${NC}"
}

log_success() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] ✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')] ⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}[$(date +'%H:%M:%S')] ❌ $1${NC}"
}

# Parsing de argumentos
BUILD=false
NO_WAIT=false
SHOW_LOGS=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build)
            BUILD=true
            shift
            ;;
        --no-wait)
            NO_WAIT=true
            shift
            ;;
        --logs)
            SHOW_LOGS=true
            shift
            ;;
        *)
            log_error "Opção desconhecida: $1"
            exit 1
            ;;
    esac
done

# Validações iniciais
log_info "🔍 Verificando pré-requisitos..."

# Verificar Docker
if ! command -v docker &> /dev/null; then
    log_error "Docker não encontrado. Instale em https://www.docker.com/get-started"
    exit 1
fi
log_success "Docker disponível"

# Verificar Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    log_error "Docker Compose não encontrado."
    exit 1
fi
log_success "Docker Compose disponível"

# Verificar se Docker Engine está rodando
log_info "🔌 Verificando se Docker Engine está ativo..."
RETRIES=0
MAX_RETRIES=5
while [ $RETRIES -lt $MAX_RETRIES ]; do
    if docker ps &> /dev/null; then
        log_success "Docker Engine ativo"
        break
    fi
    RETRIES=$((RETRIES + 1))
    if [ $RETRIES -lt $MAX_RETRIES ]; then
        log_warning "Docker Engine não está pronto. Tentando novamente em 2s..."
        sleep 2
    fi
done

if [ $RETRIES -eq $MAX_RETRIES ]; then
    log_error "Docker Engine não ficou pronto. Inicie o Docker manualmente."
    exit 1
fi

# Verificar docker-compose.yml
if [ ! -f "docker-compose.yml" ]; then
    log_error "Arquivo docker-compose.yml não encontrado no diretório atual."
    exit 1
fi
log_success "docker-compose.yml encontrado"

# Verificar containers antigos
log_info "🧹 Verificando containers existentes..."
RUNNING_COUNT=$(docker ps -q | wc -l)
if [ "$RUNNING_COUNT" -gt 0 ]; then
    log_warning "Há $RUNNING_COUNT containers em execução. Use 'docker compose down' para parar todos antes."
fi

# Build (opcional)
if [ "$BUILD" = true ]; then
    log_info "🔨 Construindo imagens (--build)..."
    docker compose build
    if [ $? -ne 0 ]; then
        log_error "Erro ao construir imagens."
        exit 1
    fi
    log_success "Imagens construídas com sucesso"
fi

# Iniciar containers
log_info "🚀 Iniciando containers em background..."
if [ "$BUILD" = true ]; then
    docker compose up -d --build
else
    docker compose up -d
fi

if [ $? -ne 0 ]; then
    log_error "Erro ao iniciar containers."
    exit 1
fi
log_success "Containers iniciados"

# Aguardar serviços ficarem saudáveis
if [ "$NO_WAIT" = false ]; then
    log_info "⏳ Aguardando serviços ficarem saudáveis..."
    MAX_WAIT=180
    ELAPSED=0
    INTERVAL=5

    while [ $ELAPSED -lt $MAX_WAIT ]; do
        HEALTHY_COUNT=$(docker compose ps -q 2>/dev/null | xargs -I {} docker inspect {} --format='{{.State.Health.Status}}' 2>/dev/null | grep -c "healthy" || echo 0)
        TOTAL_COUNT=$(docker compose ps -q 2>/dev/null | wc -l)

        if [ "$TOTAL_COUNT" -gt 0 ]; then
            log_info "  → $HEALTHY_COUNT/$TOTAL_COUNT containers saudáveis..."
        fi

        if [ "$HEALTHY_COUNT" -eq "$TOTAL_COUNT" ] && [ "$TOTAL_COUNT" -gt 0 ]; then
            log_success "Todos os serviços estão saudáveis!"
            break
        fi

        sleep $INTERVAL
        ELAPSED=$((ELAPSED + INTERVAL))

        if [ $((ELAPSED % 30)) -eq 0 ]; then
            log_warning "  ⏳ Ainda aguardando ($ELAPSED/$MAX_WAIT segundos)..."
        fi
    done

    if [ $ELAPSED -ge $MAX_WAIT ]; then
        log_warning "Timeout ao aguardar serviços (180s). Verifique os logs com: docker compose logs -f"
    fi
fi

# Exibir status
log_info "📊 Status dos containers:"
docker compose ps

# Exibir URLs de acesso
echo ""
log_info "🌐 URLs de Acesso:"
echo ""
echo -e "${GREEN}  Aplicação Web (SPA):           http://localhost:8080${NC}"
echo -e "${GREEN}  API Gateway + Swagger:         http://localhost:8080/swagger.html${NC}"
echo -e "${GREEN}  RabbitMQ Management:           http://localhost:15672 (user: eventhub / senha: eventhub_secret_2026)${NC}"
echo -e "${GREEN}  Healthcheck Gateway:           http://localhost:8080/health${NC}"
echo ""

# Exibir comandos úteis
log_info "📝 Comandos Úteis:"
echo ""
echo -e "${CYAN}  Ver logs em tempo real:        docker compose logs -f${NC}"
echo -e "${CYAN}  Ver logs de um serviço:        docker compose logs -f registration-service${NC}"
echo -e "${CYAN}  Parar todos os containers:     docker compose down${NC}"
echo -e "${CYAN}  Parar e remover volumes:       docker compose down -v${NC}"
echo -e "${CYAN}  Reconstruir imagens:           docker compose up -d --build${NC}"
echo ""

log_success "EventHub iniciado com sucesso!"

# Mostrar logs se solicitado
if [ "$SHOW_LOGS" = true ]; then
    log_info "📺 Exibindo logs em tempo real (Ctrl+C para sair)..."
    docker compose logs -f
fi
