# EventHub — Plataforma de Gestão de Eventos em Microsserviços

O **EventHub** é uma plataforma web acadêmica e profissional para gerenciamento de eventos (congressos, seminários, workshops e minicursos), projetada e implementada sobre uma **arquitetura de microsserviços poliglota** (**Java 21 / Spring Boot 3** e **Python 3.12 / FastAPI**), utilizando um **API Gateway (Nginx)** e persistência relacional em instância única do **PostgreSQL 16** totalmente conteinerizada via **Docker Compose**.

---

## 1. Visão Geral da Arquitetura

A solução adota o padrão **API Gateway + Orquestração REST Síncrona com Degradação Graciosa**, proporcionando baixa complexidade operacional, alto isolamento de responsabilidades e suporte completo às regras críticas de negócio (autenticação JWT por perfil, controle de vagas sob concorrência, lista de espera, pagamentos e notificações).

### Componentes e Microsserviços Implementados Até o Momento (Passos 1 a 5)

| Componente / Serviço | Diretório | Tecnologia | Porta Interna | Status | Responsabilidades Implementadas |
|---|---|---|---|---|---|
| **Banco de Dados Único** | `infra/postgres/` | PostgreSQL 16 Alpine | `5432` | ✅ Implementado | Esquema relacional completo (`init.sql`), constraints de integridade/concorrência e carga inicial de dados (*seed*). |
| **API Gateway & Web App** | `infra/gateway/` e `web-app/` | Nginx 1.27 + Vue.js 3 + Tailwind/DaisyUI | `8080` (Host) | ✅ Implementado | Ponto único de entrada HTTP, roteamento reverso (`/api/*`) com fallback resiliente e entrega da SPA Web com menu contextual por perfil. |
| **1. `user-service`** | `services/user-service/` | Java 21 / Spring Boot 3 | `8081` | ✅ Implementado | Cadastro de usuários (`BCrypt`), autenticação (`JWT`), manutenção de perfil e isolamento de permissões (`ORGANIZER` vs. `PARTICIPANT`). |
| **2. `event-service`** | `services/event-service/` | Java 21 / Spring Boot 3 | `8082` | ✅ Implementado | Criação, edição, publicação e cancelamento de eventos; cadastro e conclusão de atividades da programação com horários, locais, capacidade, preço e lista de espera. |
| **3. `search-service`** | `services/search-service/` | Python 3.12 / FastAPI | `8083` | ✅ Implementado | Consulta otimizada de eventos publicados, programação detalhada, filtros por palavra-chave/gratuidade e cálculo de saldo de vagas em tempo real. |
| **6. `notification-service`** | `services/notification-service/` | Python 3.12 / FastAPI | `8086` | ⏳ Próximo (Passo 6) | Envio e histórico de notificações com simulação de indisponibilidade (`503`) sem bloquear operações críticas. |
| **5. `payment-service`** | `services/payment-service/` | Python 3.12 / FastAPI | `8085` | ⏳ Planejado (Passo 7) | Inicialização e processamento de pagamentos simulados (aprovação, recusa e expiração). |
| **4. `registration-service`** | `services/registration-service/` | Java 21 / Spring Boot 3 | `8084` | ⏳ Planejado (Passo 8) | Solicitação e cancelamento de inscrições, controle de concorrência (*lock* pessimista), lista de espera e relatórios gerenciais. |

---

## 2. Pré-requisitos

Para compilar e executar todo o ecossistema localmente, você precisa **apenas** de:

- **Docker** (v24+ ou Docker Desktop)
- **Docker Compose** (v2+)
- **Git**

> **Nota**: Não é necessário instalar Java/JDK, Maven ou Python na máquina hospedeira, pois todos os microsserviços utilizam *Dockerfiles* (incluindo *multi-stage build* com Maven para os serviços Java).

---

## 3. Instalação, Compilação e Execução

### 3.1. Clonar o Repositório
```bash
git clone https://github.com/marxharon/eventhub.git
cd eventhub
```

### 3.2. Configurar Variáveis de Ambiente (Opcional)
O projeto já possui valores padrão configurados no `docker-compose.yml`, mas você pode criar um arquivo `.env` a partir do modelo fornecido:
```bash
cp .env.example .env
```

### 3.3. Compilar as Imagens e Iniciar os Containers
Execute o comando abaixo na raiz do projeto para compilar os serviços Java/Python e subir toda a infraestrutura em segundo plano:
```bash
docker compose up -d --build
```

Para verificar se todos os containers estão ativos e saudáveis (`healthy`):
```bash
docker compose ps
```

---

## 4. Acesso à Aplicação Web e Credenciais Iniciais

Com os containers em execução, acesse no navegador:

- **Aplicação Web (SPA) e API Gateway**: [http://localhost:8080](http://localhost:8080)
- **Healthcheck do Gateway**: [http://localhost:8080/health](http://localhost:8080/health)

### Contas Pré-Cadastradas (Seed Inicial)

A aplicação inicia na **Tela de Login**. Utilize uma das contas abaixo (detalhadas em [USUARIOS_INICIAIS.md](USUARIOS_INICIAIS.md)) ou crie uma nova conta diretamente pela interface:

| Perfil (`Role`) | E-mail | Senha | Funcionalidades Liberadas no Menu |
|---|---|---|---|
| **Organizador (`ORGANIZER`)** | `organizador@eventhub.com` | `123456` | Meus Eventos, Novo Evento, Programação e Nova Atividade por Evento, Credenciamento & Check-in, Relatórios & Indicadores, Validadores do Sistema, Status dos Serviços, Meu Perfil |
| **Participante (`PARTICIPANT`)** | `ana@eventhub.com` | `123456` | Explorar Eventos (Busca e Filtros), Programação e Vagas, Minhas Inscrições, Meus Pagamentos, Meus Certificados, Validadores do Sistema, Status dos Serviços, Meu Perfil |
| **Participante (`PARTICIPANT`)** | `bruno@eventhub.com` | `123456` | Mesmas permissões de Participante |
| **Participante (`PARTICIPANT`)** | `carla@eventhub.com` | `123456` | Mesmas permissões de Participante |

---

## 5. Documentação Arquitetural no Repositório

- **[PLANO_PROJETO_EVENTHUB.md](PLANO_PROJETO_EVENTHUB.md)**: Plano completo do projeto, matriz de rastreabilidade (`RF`, `RN`, `RQ`) e roteiro de implementação incremental em 10 *baby steps* autocontidos.
- **[design_arquitetural.md](design_arquitetural.md)**: Documento de *Architectural Design* contendo análise de domínio, limites de contexto (DDD), diagramas C4 (Contexto e Containers), matriz de propriedade de dados, contratos REST, fluxos de exceção e **4 ADRs** (*Architectural Decision Records*).
- **[USUARIOS_INICIAIS.md](USUARIOS_INICIAIS.md)**: Guia rápido com os usuários iniciais para teste por perfil de acesso.

---

## 6. Comandos Úteis de Operação

- **Visualizar logs de um microsserviço específico**:
  ```bash
  docker compose logs -f user-service
  docker compose logs -f event-service
  docker compose logs -f search-service
  ```
- **Parar todos os containers mantendo os dados do banco**:
  ```bash
  docker compose down
  ```
- **Reiniciar do zero (recriando o banco de dados com o seed original)**:
  ```bash
  docker compose down -v
  docker compose up -d --build
  ```
