# API Gateway (Nginx) e Integração com os Microsserviços — EventHub

Este documento explica como o **API Gateway** ([infra/gateway/nginx.conf](infra/gateway/nginx.conf)) é utilizado no **EventHub** para integrar a aplicação web SPA ([web-app/](web-app/index.html)) e o portal OpenAPI ([web-app/swagger.html](web-app/swagger.html)) aos 6 microsserviços de negócio, bem como a separação entre o tráfego externo (**Norte–Sul**) e a comunicação interna entre serviços (**Leste–Oeste**).

---

## 1. Papel Arquitetural do API Gateway

No [docker-compose.yml](docker-compose.yml), o container `eventhub-gateway` (**Nginx 1.27 Alpine**) é o **único ponto de entrada HTTP exposto na máquina hospedeira** (porta `8080`). Os 6 microsserviços (`user-service`, `event-service`, `search-service`, `registration-service`, `payment-service` e `notification-service`) não expõem portas diretamente no host e operam isolados dentro da rede bridge interna `eventhub-net`.

Suas responsabilidades principais são:

1. **Entrega da Aplicação Web (SPA) e do Portal Swagger na mesma origem (`http://localhost:8080`)**, eliminando problemas de *CORS* (*Cross-Origin Resource Sharing*) no navegador.
2. **Roteamento Reverso por Prefixo de Caminho (`/api/*`)** para os respectivos microsserviços Java (Spring Boot) e Python (FastAPI).
3. **Resolução DNS Dinâmica e Degradação Graciosa (`RQ03`, `RQ08`)**, devolvendo respostas JSON padronizadas (`HTTP 503`) caso um microsserviço esteja fora do ar ou ainda inicializando.
4. **Segurança de Borda (`RN07`, `RQ07`)**, repassando o cabeçalho `Authorization: Bearer <JWT>` aos serviços e bloqueando na borda (`HTTP 404`) endpoints exclusivos de comunicação interna entre microsserviços.
5. **Observabilidade e Rastreabilidade (`RQ06`)**, injetando e retornando `X-Correlation-ID` em todas as chamadas e registrando métricas de latência (`rt`, `uct`, `urt`) em log estruturado.

---

## 2. Diagrama de Integração (Norte–Sul vs. Leste–Oeste)

```mermaid
flowchart LR
    Browser["Navegador Web<br/>(SPA Vue 3 + Swagger UI)"] -->|HTTP :8080<br/>Tráfego Norte-Sul| GW["API Gateway<br/>(Nginx :80)"]

    subgraph Rede Interna Docker ["Rede Docker: eventhub-net"]
        GW -->|/api/users| US["1. user-service<br/>(Spring Boot :8081)"]
        GW -->|/api/events| ES["2. event-service<br/>(Spring Boot :8082)"]
        GW -->|/api/search| SS["3. search-service<br/>(FastAPI :8083)"]
        GW -->|/api/registrations| RS["4. registration-service<br/>(Spring Boot :8084)"]
        GW -->|/api/payments| PS["5. payment-service<br/>(FastAPI :8085)"]
        GW -->|/api/notifications| NS["6. notification-service<br/>(FastAPI :8086)"]

        ES -.->|POST /event/{id}/cancel-all| RS
        ES -.->|POST /send| NS
        RS -.->|POST /initiate<br/>POST /cancel-pending| PS
        RS -.->|POST /send| NS
        PS -.->|POST /payment-webhook| RS
        PS -.->|POST /send| NS
    end
```

- **Linhas contínuas (Tráfego Norte–Sul):** Requisições originadas na SPA ([web-app/js/api.js](web-app/js/api.js)), no laboratório ([web-app/js/lab-runner.js](web-app/js/lab-runner.js)) ou no Swagger UI ([web-app/swagger.html](web-app/swagger.html)) passam obrigatoriamente pelo API Gateway em `http://localhost:8080`.
- **Linhas tracejadas (Tráfego Leste–Oeste):** Chamadas síncronas entre microsserviços utilizam diretamente o DNS interno do Docker (`http://<nome-do-servico>:<porta>`) na rede `eventhub-net`, sem passar pelo gateway.

---

## 3. Tabela de Roteamento Reverso (`/api/*`)

| Prefixo no Gateway (`:8080`) | Upstream Interno (`eventhub-net`) | Serviço | Stack | Fallback de Indisponibilidade (`502/503/504`) |
|---|---|---|---|---|
| `/health` | *(Respondido pelo próprio Nginx)* | `api-gateway` | Nginx 1.27 | `200 {"status":"UP","component":"api-gateway"}` |
| `/api/users` | `http://user-service:8081` | `user-service` | Java 21 / Spring Boot 3 | `@service_unavailable_users` (`HTTP 503` JSON) |
| `/api/events` | `http://event-service:8082` | `event-service` | Java 21 / Spring Boot 3 | `@service_unavailable_events` (`HTTP 503` JSON) |
| `/api/search` | `http://search-service:8083` | `search-service` | Python 3.12 / FastAPI | `@service_unavailable_search` (`HTTP 503` JSON) |
| `/api/registrations` | `http://registration-service:8084` | `registration-service` | Java 21 / Spring Boot 3 | `@service_unavailable_registrations` (`HTTP 503` JSON) |
| `/api/payments` | `http://payment-service:8085` | `payment-service` | Python 3.12 / FastAPI | `@service_unavailable_payments` (`HTTP 503` JSON) |
| `/api/notifications` | `http://notification-service:8086` | `notification-service` | Python 3.12 / FastAPI | `@service_unavailable_notifications` (`HTTP 503` JSON) |
| `/` | `/usr/share/nginx/html` | SPA Web & Swagger | Vue 3 + Tailwind / DaisyUI | `try_files $uri $uri/ /index.html` |

---

## 4. Mecanismos Técnicos Configurados no `nginx.conf`

### 4.1. Resolução DNS Dinâmica e Subida Independente (`RQ01`, `RQ03`, `RQ08`)
```nginx
resolver 127.0.0.11 valid=10s ipv6=off;
```
Ao usar o resolvedor DNS embutido do Docker (`127.0.0.11`) combinado com variáveis nos blocos `location` (ex.: `set $event_upstream http://event-service:8082; proxy_pass $event_upstream;`), o Nginx não falha na partida caso algum microsserviço ainda esteja compilando ou seja reiniciado individualmente.

### 4.2. Interceptação de Erros e Resposta JSON Amigável (`RQ03`)
Quando um container de microsserviço é parado (por exemplo, `docker compose stop search-service`), o Nginx intercepta os erros `502 Bad Gateway`, `503 Service Unavailable` e `504 Gateway Timeout` (`proxy_intercept_errors on;`) e devolve um payload JSON estruturado:
```json
{
  "status": "DOWN",
  "service": "search-service",
  "stack": "Python 3.12 / FastAPI",
  "message": "Serviço de Busca ainda não iniciado ou indisponível."
}
```
Isso permite que o cliente HTTP da SPA ([web-app/js/api.js](web-app/js/api.js)) e o painel **Status dos Serviços** tratem a indisponibilidade sem quebrar a interface.

### 4.3. Repasse de JWT e Isolamento de Endpoints Internos (`RN07`, `RQ07`)
- **Propagação do Token JWT:**
  ```nginx
  proxy_set_header Authorization $http_authorization;
  ```
  O token JWT obtido em `POST /api/users/login` é armazenado pela SPA e enviado no cabeçalho `Authorization: Bearer <token>`. O gateway repassa esse cabeçalho integralmente aos microsserviços Java (`JwtAuthFilter`) e Python (`auth.py`), que validam a assinatura HMAC (`JWT_SECRET`), o `userId` e a `role` (`ORGANIZER` ou `PARTICIPANT`).
- **Bloqueio de Endpoints Internos na Borda:**
  Endpoints que compõem a orquestração interna entre serviços são explicitamente bloqueados no gateway para chamadas vindas de fora da rede Docker:
  - `POST /api/registrations/payment-webhook` $\rightarrow$ `404 {"status":"NOT_FOUND","message":"Endpoint interno."}`
  - `POST /api/payments/initiate` $\rightarrow$ `404 {"status":"NOT_FOUND","message":"Endpoint interno."}`
  - `POST /api/payments/registration/{id}/cancel-pending` $\rightarrow$ `404 {"status":"NOT_FOUND","message":"Endpoint interno."}`

### 4.4. Correlação de Requisições e Logs de Latência (`RQ06`)
```nginx
log_format eventhub_log '$remote_addr - [$time_local] "$request" '
                        'status=$status bytes=$body_bytes_sent '
                        'rt=$request_time uct="$upstream_connect_time" urt="$upstream_response_time"';

proxy_set_header X-Correlation-ID $request_id;
add_header X-Correlation-ID $request_id always;
```
Cada requisição recebe um identificador único (`$request_id`) propagado aos microsserviços e devolvido ao navegador no cabeçalho `X-Correlation-ID`, permitindo rastrear chamadas ponta a ponta e auditar o tempo de resposta de cada upstream.

---

## 5. Como a Aplicação Web (SPA) Consome o Gateway

No arquivo [web-app/js/api.js](web-app/js/api.js), todas as chamadas utilizam caminhos relativos à mesma origem do gateway (`/api/...`), anexando automaticamente o token JWT da sessão e medindo a latência e o `X-Correlation-ID` devolvidos pelo Nginx:

1. **Autenticação e Perfil (`user-service`):** `POST /api/users/login`, `POST /api/users/register`, `GET /api/users/me`, `PUT /api/users/me`, `GET /api/users/{id}`.
2. **Gestão de Eventos, Programação, Check-in e Certificados (`event-service`):** `GET/POST/PUT /api/events`, `PATCH /api/events/{id}/publish|cancel`, `POST/PUT /api/events/.../activities`, `POST /api/events/attendance/check-in`, `POST /api/events/certificates/issue`, `GET /api/events/certificates/my`, `GET /api/events/certificates/validate/{code}`.
3. **Catálogo Público e Busca (`search-service`):** `GET /api/search/events` e `GET /api/search/activities` (com filtros `keyword`, `organization`, `onlyFree`, `onlyAvailable`).
4. **Inscrições, Fila de Espera e Relatórios (`registration-service`):** `POST /api/registrations`, `GET /api/registrations/my`, `GET /api/registrations/{id}`, `PATCH /api/registrations/{id}/cancel`, `GET /api/registrations/reports/event/{eventId}`.
5. **Checkout Simulado (`payment-service`):** `GET /api/payments/registration/{registrationId}`, `POST /api/payments/{paymentId}/confirm|refuse|expire`.
6. **Central de Notificações e Simulação de Falha (`notification-service`):** `GET /api/notifications/user/{userId}`, `PATCH /api/notifications/{id}/read`, `DELETE /api/notifications/{id}`, `POST /api/notifications/simulate-failure`.
