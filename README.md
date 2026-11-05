# EventHub — Plataforma de Gestão de Eventos em Microsserviços

O **EventHub** é uma plataforma web acadêmica e profissional para gerenciamento de eventos (congressos, seminários, workshops e minicursos), projetada e implementada sobre uma **arquitetura de microsserviços poliglota** (**Java 21 / Spring Boot 3** e **Python 3.12 / FastAPI**), utilizando um **API Gateway (Nginx)** e persistência relacional em instância única do **PostgreSQL 16** totalmente conteinerizada via **Docker Compose**.

---

## 1. Visão Geral da Arquitetura

A solução adota o padrão **API Gateway + Orquestração REST Síncrona com Degradação Graciosa**, proporcionando baixa complexidade operacional, alto isolamento de responsabilidades e suporte completo às regras críticas de negócio (autenticação JWT por perfil, controle de vagas sob concorrência, lista de espera, pagamentos e notificações).

### Componentes e Microsserviços Implementados Até o Momento (Passos 1 a 10)

| Componente / Serviço | Diretório | Tecnologia | Porta Interna | Status | Responsabilidades Implementadas |
|---|---|---|---|---|---|
| **Banco de Dados Único** | `infra/postgres/` | PostgreSQL 16 Alpine | `5432` | ✅ Implementado | Esquema relacional completo (`init.sql`), constraints de integridade/concorrência e carga inicial de dados (*seed*). |
| **API Gateway & Web App** | `infra/gateway/` e `web-app/` | Nginx 1.27 + Vue.js 3 + Tailwind/DaisyUI | `8080` (Host) | ✅ Implementado | Ponto único de entrada HTTP, roteamento reverso (`/api/*`) com fallback resiliente e entrega da SPA Web com menu contextual por perfil. |
| **1. `user-service`** | `services/user-service/` | Java 21 / Spring Boot 3 | `8081` | ✅ Implementado | Cadastro de usuários (`BCrypt`), autenticação (`JWT`), manutenção de perfil e isolamento de permissões (`ORGANIZER` vs. `PARTICIPANT`). |
| **2. `event-service`** | `services/event-service/` | Java 21 / Spring Boot 3 | `8082` | ✅ Implementado | Criação, edição, publicação e cancelamento de eventos; cadastro e conclusão de atividades da programação com horários, locais, capacidade, preço e lista de espera; registro de presença (*check-in*) e emissão/validação de certificados (RF09, RF10, RN06). |
| **3. `search-service`** | `services/search-service/` | Python 3.12 / FastAPI | `8083` | ✅ Implementado | Consulta otimizada de eventos publicados, programação detalhada, filtros por palavra-chave/gratuidade e cálculo de saldo de vagas em tempo real. |
| **6. `notification-service`** | `services/notification-service/` | Python 3.12 / FastAPI | `8086` | ✅ Implementado | Envio e histórico de notificações com simulação de indisponibilidade (`503`) sem bloquear operações críticas. |
| **5. `payment-service`** | `services/payment-service/` | Python 3.12 / FastAPI | `8085` | ✅ Implementado | Inicialização, confirmação, recusa e expiração de pagamentos simulados; webhook para `registration-service` e aviso resiliente ao `notification-service`. |
| **4. `registration-service`** | `services/registration-service/` | Java 21 / Spring Boot 3 | `8084` | ✅ Implementado (Passo 8) | Solicitação, consulta e cancelamento de inscrições, controle de concorrência (*lock* pessimista), lista de espera, webhook de resultado de pagamento e relatórios gerenciais por evento (RF11). |

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

## 4. Acesso à Aplicação Web, Documentação Swagger e Credenciais Iniciais

Com os containers em execução, acesse no navegador:

- **Aplicação Web (SPA) e API Gateway**: [http://localhost:8080](http://localhost:8080)
- **Portal Unificado Swagger UI / OpenAPI 3.0**: [http://localhost:8080/swagger.html](http://localhost:8080/swagger.html)
- **Healthcheck do Gateway**: [http://localhost:8080/health](http://localhost:8080/health)

### Documentação Interativa das APIs (Swagger / OpenAPI 3.0)

Todos os microsserviços expõem seus contratos OpenAPI 3.0 através do API Gateway. Você pode acessar a documentação clicando no link **"Documentação das APIs (Swagger / OpenAPI)"** na **Página de Login**, no botão **"Swagger API Docs"** na **barra de cabeçalho superior do sistema**, ou diretamente pelas URLs abaixo:

| Microsserviço | Stack | Especificação OpenAPI (JSON) | Interface Interativa (Swagger UI) |
|---|---|---|---|
| **Portal Unificado (Todos os Serviços)** | Swagger UI Standalone | — | [http://localhost:8080/swagger.html](http://localhost:8080/swagger.html) |
| **1. `user-service`** | Java 21 / Spring Boot 3 (SpringDoc) | [http://localhost:8080/api/users/openapi.json](http://localhost:8080/api/users/openapi.json) | [Abrir no Portal Swagger](http://localhost:8080/swagger.html) |
| **2. `event-service`** | Java 21 / Spring Boot 3 (SpringDoc) | [http://localhost:8080/api/events/openapi.json](http://localhost:8080/api/events/openapi.json) | [Abrir no Portal Swagger](http://localhost:8080/swagger.html) |
| **3. `search-service`** | Python 3.12 / FastAPI | [http://localhost:8080/api/search/openapi.json](http://localhost:8080/api/search/openapi.json) | [http://localhost:8080/api/search/docs](http://localhost:8080/api/search/docs) |
| **5. `payment-service`** | Python 3.12 / FastAPI | [http://localhost:8080/api/payments/openapi.json](http://localhost:8080/api/payments/openapi.json) | [http://localhost:8080/api/payments/docs](http://localhost:8080/api/payments/docs) |
| **4. `registration-service`** | Java 21 / Spring Boot 3 (SpringDoc) | [http://localhost:8080/api/registrations/openapi.json](http://localhost:8080/api/registrations/openapi.json) | [Abrir no Portal Swagger](http://localhost:8080/swagger.html) |
| **6. `notification-service`** | Python 3.12 / FastAPI | [http://localhost:8080/api/notifications/openapi.json](http://localhost:8080/api/notifications/openapi.json) | [http://localhost:8080/api/notifications/docs](http://localhost:8080/api/notifications/docs) |

> **Dica de Autenticação no Swagger**: Para testar endpoints protegidos (`POST`, `PUT`, `PATCH`), faça login em `POST /api/users/login`, copie o `token` retornado e clique no botão **"Authorize"** no topo do Swagger UI.

### Endpoints Adicionados nos Passos 8 a 10

Além dos endpoints de base (usuários, eventos, busca), os serviços abaixo foram acrescentados. Todos são acessados pelo API Gateway em `http://localhost:8080`.

| Serviço | Método | Endpoint | Descrição |
|---|---|---|---|
| **event-service** | `POST` | `/api/events/attendance/check-in` | Registra a presença (*check-in*) de uma inscrição (RF09). |
| **event-service** | `POST` | `/api/events/certificates/issue` | Emite certificado de participação para inscrição com presença registrada (RN06). |
| **event-service** | `GET` | `/api/events/certificates/my` | Lista os certificados do participante autenticado. |
| **event-service** | `GET` | `/api/events/certificates/validate/{validationCode}` | Valida a autenticidade de um certificado pelo código. |
| **registration-service** | `POST` | `/api/registrations` | Cria inscrição com controle de vagas, lista de espera e prevenção de duplicidade (RN01, RN04). |
| **registration-service** | `GET` | `/api/registrations/my` | Lista as inscrições do participante autenticado. |
| **registration-service** | `GET` | `/api/registrations/{registrationId}` | Consulta uma inscrição do próprio usuário. |
| **registration-service** | `PATCH` | `/api/registrations/{registrationId}/cancel` | Cancela inscrição, libera a vaga e promove o próximo da lista de espera (RN04). |
| **registration-service** | `POST` | `/api/registrations/payment-webhook` | Recebe o resultado do pagamento (`payment-service`) e confirma ou libera a vaga (RN02, RN03). |
| **registration-service** | `GET` | `/api/registrations/reports/event/{eventId}` | Relatório gerencial do evento para o organizador: totais e indicadores por atividade (RF11, RN07). |
| **payment-service** | `POST` | `/api/payments/initiate` | Inicia um pagamento simulado para uma inscrição pendente (RF07, RN02). |
| **payment-service** | `GET` | `/api/payments/registration/{registrationId}` | Consulta o pagamento associado a uma inscrição. |
| **payment-service** | `POST` | `/api/payments/{paymentId}/confirm` | Confirma o pagamento (`APPROVED`). |
| **payment-service** | `POST` | `/api/payments/{paymentId}/refuse` | Recusa o pagamento (`REFUSED`), cenário RN03. |
| **payment-service** | `POST` | `/api/payments/{paymentId}/expire` | Expira o pagamento (`EXPIRED`), cenário RN03. |
| **notification-service** | `GET` | `/api/notifications/simulate-failure` | Consulta se a simulação de indisponibilidade está ativa. |
| **notification-service** | `POST` | `/api/notifications/simulate-failure?fail=true\|false` | Liga ou desliga a simulação de falha, que faz `/send` responder `503` (RN05). |
| **notification-service** | `POST` | `/api/notifications/send` | Envia uma notificação (retorna `503` quando a falha simulada está ativa). |

> Os endpoints de `search-service`, `user-service` e `notification-service` (leitura, marcação como lida e remoção) continuam disponíveis no Swagger Portal.

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

---

## 7. Aplicação Web (SPA) e Chaos Lab

A SPA em `http://localhost:8080` organiza as telas por perfil:

- **Participante**: Explorar Eventos, Minhas Inscrições, Meus Pagamentos (checkout simulado com aprovação, recusa ou expiração), Meus Certificados (visualização e verificador público de autenticidade).
- **Organizador**: Meus Eventos, Programação e Atividades, Credenciamento & Check-in (terminal de presença), Relatórios & Indicadores (gráficos com Chart.js).
- **Validadores do Sistema**: Status dos Serviços e **Chaos Lab** (tela administrativa de validação arquitetural).

### Chaos Lab

O Chaos Lab executa, pelo navegador, os cenários de validação do `web-app/js/lab-runner.js`:

| Cenário | Requisito | O que faz |
|---|---|---|
| Concorrência de vagas | RN01 | Dispara inscrições paralelas em uma atividade com vagas limitadas e confirma que não há *overbooking*. |
| Falha de notificação | RN05 | Liga a simulação de indisponibilidade do `notification-service`, envia notificações e confirma que os fluxos críticos continuam funcionando. |
| Recusa de pagamento | RN03 | Cria um pagamento, recusa-o e verifica a liberação da vaga. |

Para executar, entre como `organizador@eventhub.com` (senha `123456`) e acesse **Chaos Lab** no menu lateral.
