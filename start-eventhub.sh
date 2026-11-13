#!/bin/bash
# EventHub Script de Inicializacao (Bash)
# ==========================================
# Inicia todos os microservicos e infraestrutura do EventHub
# Uso: ./start-eventhub.sh [opcoes]

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Funcoes
log_info() {
    echo -e "${CYAN}[$(date +'%H:%M:%S')] $1${NC}"
}

log_success() {
    echo -e "${GREEN}[$(date +'%H:%M:%S')] OK: $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%H:%M:%S')] Aviso: $1${NC}"
}

log_error() {
    echo -e "${RED}[$(date +'%H:%M:%S')] Erro: $1${NC}"
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
            log_error "Opcao desconhecida: $1"
            echo "Uso: $0 [--build] [--no-wait] [--logs]"
            exit 1
            ;;
    esac
done

# Validacoes iniciais
log_info "Verificando pre-requisitos..."

# Verificar Docker
if ! command -v docker &> /dev/null; then
    log_error "Docker nao encontrado. Instale em https://www.docker.com/products/docker-desktop"
    exit 1
fi
log_success "Docker disponivel"

# Verificar Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose --version &> /dev/null; then
    log_error "Docker Compose nao encontrado."
    exit 1
fi
log_success "Docker Compose disponivel"

# Verificar se Docker Engine esta rodando
log_info "Verificando se Docker Engine esta ativo..."
RETRIES=0
MAX_RETRIES=5
while [ $RETRIES -lt $MAX_RETRIES ]; do
    if docker ps &> /dev/null; then
        log_success "Docker Engine ativo"
        break
    fi
    RETRIES=$((RETRIES + 1))
    if [ $RETRIES -lt $MAX_RETRIES ]; then
        log_warning "Docker Engine nao esta pronto. Tentando novamente em 2s..."
        sleep 2
    fi
done

if [ $RETRIES -eq $MAX_RETRIES ]; then
    log_error "Docker Engine nao ficou pronto. Inicie manualmente."
    exit 1
fi

# Verificar arquivo docker-compose.yml
if [ ! -f "docker-compose.yml" ]; then
    log_error "Arquivo docker-compose.yml nao encontrado no diretorio atual."
    exit 1
fi
log_success "docker-compose.yml encontrado"

# Limpar containers antigos (opcional)
log_info "Verificando containers existentes..."
RUNNING=$(docker ps -q | wc -l)
if [ $RUNNING -gt 0 ]; then
    log_warning "Ha $RUNNING containers em execucao. Use 'docker compose down' para parar."
fi

# Build (opcional)
if [ "$BUILD" = true ]; then
    log_info "Construindo imagens (--build)..."
    docker compose build
    if [ $? -ne 0 ]; then
        log_error "Erro ao construir imagens."
        exit 1
    fi
    log_success "Imagens construidas com sucesso"
fi

# Iniciar containers
log_info "Iniciando containers em background..."
docker compose up -d --build
if [ $? -ne 0 ]; then
    log_error "Erro ao iniciar containers."
    exit 1
fi
log_success "Containers iniciados"

# Aguardar servicos ficarem saudaveis
if [ "$NO_WAIT" = false ]; then
    log_info "Aguardando servicos ficarem saudaveis..."
    MAX_WAIT=180
    ELAPSED=0
    INTERVAL=5

    while [ $ELAPSED -lt $MAX_WAIT ]; do
        HEALTHY_COUNT=$(docker compose ps -q | while read CONTAINER_ID; do
            docker inspect "$CONTAINER_ID" --format='{{.State.Health.Status}}' 2>/dev/null
        done | grep -c "healthy" || true)

        TOTAL_COUNT=$(docker compose ps -q | wc -l)

        if [ $HEALTHY_COUNT -gt 0 ]; then
            log_info "  Status: $HEALTHY_COUNT/$TOTAL_COUNT containers saudaveis..."
        fi

        if [ $HEALTHY_COUNT -eq $TOTAL_COUNT ] && [ $TOTAL_COUNT -gt 0 ]; then
            log_success "Todos os servicos estao saudaveis!"
            break
        fi

        sleep $INTERVAL
        ELAPSED=$((ELAPSED + INTERVAL))

        if [ $((ELAPSED % 30)) -eq 0 ]; then
            log_warning "Ainda aguardando ($ELAPSED/$MAX_WAIT segundos)..."
        fi
    done

    if [ $ELAPSED -ge $MAX_WAIT ]; then
        log_warning "Timeout ao aguardar servicos (180s). Verifique logs com: docker compose logs -f"
    fi
fi

# Exibir status
log_info "Status dos containers:"
docker compose ps

# Exibir URLs de acesso
log_info "URLs de Acesso:"
echo ""
echo -e "${GREEN}  Aplicacao Web (SPA):           http://localhost:8080${NC}"
echo -e "${GREEN}  API Gateway + Swagger:         http://localhost:8080/swagger.html${NC}"
echo -e "${GREEN}  RabbitMQ Management:           http://localhost:15672 (user: eventhub / senha: eventhub_secret_2026)${NC}"
echo -e "${GREEN}  Healthcheck Gateway:           http://localhost:8080/health${NC}"
echo ""

# Exibir comandos uteis
log_info "Comandos Uteis:"
echo ""
echo -e "${CYAN}  Ver logs em tempo real:        docker compose logs -f${NC}"
echo -e "${CYAN}  Ver logs de um servico:        docker compose logs -f registration-service${NC}"
echo -e "${CYAN}  Parar todos os containers:     docker compose down${NC}"
echo -e "${CYAN}  Parar e remover volumes:       docker compose down -v${NC}"
echo -e "${CYAN}  Reconstruir imagens:           docker compose up -d --build${NC}"
echo ""

log_success "EventHub iniciado com sucesso!"

# Mostrar logs se solicitado
if [ "$SHOW_LOGS" = true ]; then
    log_info "Exibindo logs em tempo real (Ctrl+C para sair)..."
    docker compose logs -f
fi
