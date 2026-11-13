# 🚀 Scripts de Inicialização — Resumo Executivo

## ⚡ Comando Rápido para Iniciar

### **Windows (PowerShell)**
```powershell
.\start-eventhub.ps1
```

### **Linux / macOS (Bash)**
```bash
./start-eventhub.sh
```

---

## 📋 O que Cada Script Faz

✅ Verifica se Docker e Docker Compose estão instalados  
✅ Valida que o Docker Engine está ativo  
✅ Inicia 6 microsserviços + infraestrutura  
✅ Aguarda health checks (até 180 segundos)  
✅ Exibe URLs de acesso e comandos úteis  

---

## 🎯 Opções Disponíveis

| Opção | Windows | Linux/macOS | Descrição |
|---|---|---|---|
| Padrão | `.\start-eventhub.ps1` | `./start-eventhub.sh` | Inicia com build automático |
| Reconstrói | `.\start-eventhub.ps1 -Build` | `./start-eventhub.sh --build` | Reconstrói todas as imagens Docker |
| Logs | `.\start-eventhub.ps1 -Logs` | `./start-eventhub.sh --logs` | Mostra logs em tempo real |
| Sem Aguardar | `.\start-eventhub.ps1 -NoWait` | `./start-eventhub.sh --no-wait` | Não aguarda health checks |
| Combinado | `.\start-eventhub.ps1 -Build -Logs` | `./start-eventhub.sh --build --logs` | Reconstrói e mostra logs |

---

## 🌐 URLs Após Inicialização

| Serviço | URL |
|---|---|
| 🌍 **Aplicação Web** | http://localhost:8080 |
| 📚 **API Gateway (Swagger)** | http://localhost:8080/swagger.html |
| 🐰 **RabbitMQ Management** | http://localhost:15672 |
| 🏥 **Health Check** | http://localhost:8080/health |

**RabbitMQ Login:**
- Usuário: `eventhub`
- Senha: `eventhub_secret_2026`

---

## 👤 Usuários para Teste

| Email | Role | Senha |
|---|---|---|
| maria@eventhub.com | ORGANIZER | 123456 |
| joao@eventhub.com | PARTICIPANT | 123456 |
| ana@eventhub.com | PARTICIPANT | 123456 |

---

## 🛠️ Comandos Úteis Durante Execução

```bash
# Ver todos os logs em tempo real
docker compose logs -f

# Ver logs de um serviço específico
docker compose logs -f registration-service

# Ver status dos containers
docker compose ps

# Parar todos os containers
docker compose down

# Parar e remover volumes (apaga dados)
docker compose down -v

# Reconstruir imagens
docker compose up -d --build
```

---

## ⚠️ Troubleshooting Rápido

### "Docker not found"
- Instale Docker Desktop: https://www.docker.com/products/docker-desktop
- Windows: Inicie o Docker Desktop manualmente

### "Port 8080 already in use"
```bash
docker compose down
# Aguarde 30s e tente novamente
```

### "Containers failing"
```bash
# Ver logs detalhados
docker compose logs -f

# Reconstruir e limpar
docker compose down -v
docker compose up -d --build
```

### "Health checks timeout"
```bash
# É normal para a primeira inicialização
# Aguarde até 3 minutos
# Ou execute com a opção -NoWait (PowerShell) / --no-wait (Bash)
```

---

## 📁 Arquivos Criados

- **`start-eventhub.ps1`** — Script PowerShell para Windows
- **`start-eventhub.sh`** — Script Bash para Linux/macOS
- **`START_GUIDE.md`** — Documentação completa com troubleshooting
- **`STARTUP_SUMMARY.md`** — Este arquivo (resumo executivo)

---

## 📚 Documentação Relacionada

- [README.md](README.md) — Visão geral do projeto
- [START_GUIDE.md](START_GUIDE.md) — Guia de inicialização completo
- [IDENTIFICACAO_FILA_MENSAGERIA.md](IDENTIFICACAO_FILA_MENSAGERIA.md) — RabbitMQ setup
- [ANALISE_ARQUITETURAL_CRITICA.md](ANALISE_ARQUITETURAL_CRITICA.md) — Análise da arquitetura

---

## ✨ Próximas Etapas

1. Execute o script de inicialização
2. Aguarde até 3 minutos enquanto os serviços inicializam
3. Acesse http://localhost:8080
4. Faça login com um dos usuários de teste
5. Explore o Chaos Lab: Menu → ⚡ Validação Arquitetural & Chaos Lab

---

**Criado em:** 2026-11-12  
**Commit:** 756cab3
