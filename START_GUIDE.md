# 🚀 Guia de Inicialização — EventHub

## Início Rápido

### **Windows (PowerShell)**

```powershell
# Executar no PowerShell (como administrador recomendado)
.\start-eventhub.ps1

# Ou com opções:
.\start-eventhub.ps1 -Build        # Reconstrói as imagens
.\start-eventhub.ps1 -Logs         # Mostra logs em tempo real
.\start-eventhub.ps1 -NoWait       # Não espera serviços ficarem saudáveis
.\start-eventhub.ps1 -Build -Logs  # Reconstrói e mostra logs
```

### **Linux / macOS (Bash)**

```bash
# Executar no terminal
chmod +x start-eventhub.sh         # Dar permissão de execução (primeira vez)
./start-eventhub.sh

# Ou com opções:
./start-eventhub.sh --build        # Reconstrói as imagens
./start-eventhub.sh --logs         # Mostra logs em tempo real
./start-eventhub.sh --no-wait      # Não espera serviços ficarem saudáveis
./start-eventhub.sh --build --logs # Reconstrói e mostra logs
```

---

## 📋 O que os Scripts Fazem

1. ✅ **Verifica Pré-requisitos**
   - Docker instalado e ativo
   - Docker Compose disponível
   - `docker-compose.yml` presente

2. ⏳ **Inicia Containers**
   - 6 microsserviços (user, event, registration, payment, notification, search)
   - PostgreSQL 16
   - RabbitMQ 3.13
   - Nginx API Gateway

3. 🏥 **Aguarda Health Checks**
   - Valida que todos os serviços estão saudáveis (até 180s)
   - Exibe progresso em tempo real

4. 🌐 **Exibe URLs de Acesso**
   - Aplicação Web
   - Swagger/OpenAPI
   - RabbitMQ Management
   - Health Check

5. 📝 **Lista Comandos Úteis**
   - Ver logs
   - Parar containers
   - Reconstruir imagens

---

## 🌐 URLs de Acesso (Após Inicialização)

| Serviço | URL | Login |
|---|---|---|
| **Aplicação Web** | http://localhost:8080 | Cadastre-se ou use usuários iniciais |
| **API Gateway (Swagger)** | http://localhost:8080/swagger.html | Teste endpoints |
| **RabbitMQ Management** | http://localhost:15672 | user: `eventhub` / senha: `eventhub_secret_2026` |
| **Healthcheck** | http://localhost:8080/health | Verifica status |

---

## 📝 Usuários Iniciais para Teste

| Email | Role | Senha |
|---|---|---|
| `maria@eventhub.com` | ORGANIZER | `123456` |
| `joao@eventhub.com` | PARTICIPANT | `123456` |
| `ana@eventhub.com` | PARTICIPANT | `123456` |

---

## 🛠️ Troubleshooting

### Docker não está pronto

**Problema:** Erro "Docker Engine não ficou pronto"

**Solução:**
```powershell
# Windows: Abra o Docker Desktop manualmente
# Ou reinicie: Restart-Service docker (como admin)

# Linux/Mac:
sudo systemctl restart docker
# ou
open /Applications/Docker.app  # macOS
```

### Containers falhando

**Verificar logs:**
```bash
docker compose logs -f                          # Todos
docker compose logs -f registration-service     # Específico
docker compose ps                               # Ver status
```

### Reconstruir tudo do zero

```bash
docker compose down -v                          # Deleta containers + volumes
./start-eventhub.sh --build                     # Reconstrói e inicia
```

### Porta 8080 já em uso

```bash
# Encontrar quem está usando a porta
netstat -ano | findstr :8080           # Windows
lsof -i :8080                          # Linux/Mac

# Liberar manualmente se necessário
docker compose down
```

---

## 📊 Monitorando a Execução

### Ver todos os logs em tempo real
```bash
docker compose logs -f
```

### Ver logs de um serviço específico
```bash
docker compose logs -f registration-service
docker compose logs -f notification-service
docker compose logs -f payment-service
```

### Ver status dos containers
```bash
docker compose ps
```

### Inspecionar um container
```bash
docker compose exec registration-service bash
```

---

## ✨ Próximas Etapas

1. **Acesse a aplicação:** http://localhost:8080
2. **Faça login** com um usuário da tabela acima
3. **Crie um evento** (como ORGANIZER)
4. **Inscreva-se** (como PARTICIPANT)
5. **Teste o Chaos Lab:** Menu → ⚡ Validação Arquitetural & Chaos Lab

---

## 🔧 Parar os Serviços

```bash
# Parar mantendo volumes (dados persistem)
docker compose down

# Parar e remover tudo (apaga dados)
docker compose down -v
```

---

## 📚 Referências

- **Documentação do Projeto:** [README.md](README.md)
- **Identificação de Fila de Mensageria:** [IDENTIFICACAO_FILA_MENSAGERIA.md](IDENTIFICACAO_FILA_MENSAGERIA.md)
- **Análise Crítica da Arquitetura:** [ANALISE_ARQUITETURAL_CRITICA.md](ANALISE_ARQUITETURAL_CRITICA.md)
- **Plano do Projeto:** [PLANO_PROJETO_EVENTHUB.md](PLANO_PROJETO_EVENTHUB.md)

---

**Última atualização:** 2026-11-12  
**Versão:** 1.0
