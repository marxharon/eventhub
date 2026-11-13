# 📡 Identificação da Utilização da Fila de Mensageria (RabbitMQ) no Projeto EventHub

## 🎯 Resumo Executivo

A fila de mensageria RabbitMQ é utilizada em **3 camadas** da arquitetura:

1. **Produtores (Publishers)**: `registration-service`, `event-service`, `payment-service`
2. **Broker**: RabbitMQ 3.13 Management Alpine (no `docker-compose.yml`)
3. **Consumidor (Subscriber)**: `notification-service`

---

## 1. Configuração da Fila de Mensageria

### 1.1 Arquivo: `docker-compose.yml`

```yaml
rabbitmq:
  image: rabbitmq:3.13-management-alpine
  container_name: eventhub-rabbitmq
  environment:
    RABBITMQ_DEFAULT_USER: eventhub
    RABBITMQ_DEFAULT_PASS: eventhub_secret_2026
  ports:
    - "5672:5672"      # AMQP
    - "15672:15672"    # Management UI
  volumes:
    - rabbitmq_data:/var/lib/rabbitmq
```

**UI RabbitMQ**: http://localhost:15672 (user: eventhub / password: eventhub_secret_2026)

---

## 2. Topologia AMQP — Exchanges, Queues, DLX/DLQ

### 2.1 Exchange Principal (Topic Exchange)

| Atributo | Valor |
|---|---|
| **Nome** | `eventhub.domain.events` |
| **Tipo** | Topic (roteamento por padrão) |
| **Durável** | ✅ true |
| **Arquivo de Configuração** | `RabbitMqConfig.java` (Java) |

**Localização no Código:**

- **Java** (registration-service e event-service):
  - 📁 `services/registration-service/src/main/java/com/eventhub/registration/config/RabbitMqConfig.java` (linha 14)
  - 📁 `services/event-service/src/main/java/com/eventhub/event/config/RabbitMqConfig.java` (linha 14)

```java
public static final String DOMAIN_EXCHANGE = "eventhub.domain.events";

@Bean
public TopicExchange domainEventsExchange(
    @Value("${eventhub.messaging.exchange:" + DOMAIN_EXCHANGE + "}") String exchangeName
) {
    return new TopicExchange(exchangeName, true, false);
}
```

- **Python** (payment-service):
  - 📁 `services/payment-service/app/services/downstream_client.py` (linha 13)

```python
EXCHANGE_NAME = "eventhub.domain.events"
```

---

### 2.2 Filas Principais

| Queue | Routing Keys Bound | Dead Letter Exchange | Propósito |
|---|---|---|---|
| **`eventhub.notifications.queue`** | `notification.#`, `event.#`, `registration.#`, `payment.#` | `eventhub.domain.dlx` | Consumida por `notification-service` |
| **`eventhub.notifications.dlq`** (Dead Letter Queue) | `#` (de dlx) | — | Armazena mensagens rejeitadas/expiradas |

**Arquivo de Configuração:**

- 📁 `services/notification-service/app/services/amqp_consumer.py` (linhas 11-75)

```python
EXCHANGE_NAME = "eventhub.domain.events"
DLX_NAME = "eventhub.domain.dlx"
QUEUE_NAME = "eventhub.notifications.queue"
DLQ_NAME = "eventhub.notifications.dlq"

# Dead Letter Exchange binds para DLQ
dlx_exchange = await channel.declare_exchange(
    DLX_NAME, ExchangeType.TOPIC, durable=True
)
dlq = await channel.declare_queue(DLQ_NAME, durable=True)
await dlq.bind(dlx_exchange, routing_key="#")

# Fila principal com x-dead-letter-exchange
queue = await channel.declare_queue(
    QUEUE_NAME,
    durable=True,
    arguments={"x-dead-letter-exchange": DLX_NAME},
)
```

---

## 3. Produtores (Publishers) de Mensagens

### 3.1 `registration-service` — Publica Eventos de Inscrição

**Arquivo Principal:** 📁 `services/registration-service/src/main/java/com/eventhub/registration/service/ExternalIntegrationService.java`

**Método Chave:** `notifyUser()` → `dispatchNotification()`

```java
public void notifyUser(Long userId, String eventType, String message) {
    // ... busca email do usuário ...
    
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        // AFTER_COMMIT: registra callback para disparar APÓS commit (RN05)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchNotification(userId, recipientEmail, eventType, message);
            }
        });
    }
}

private void dispatchNotification(Long userId, String recipientEmail, String eventType, String message) {
    Map<String, Object> payload = Map.of(
        "userId", userId,
        "recipientEmail", recipientEmail,
        "eventType", eventType,
        "message", message
    );

    if (messagingEnabled) {
        String routingKey = "notification.registration." + eventType.toLowerCase();
        Map<String, Object> envelope = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "occurredAt", Instant.now().toString(),
            "sourceService", "registration-service",
            "routingKey", routingKey,
            "payload", payload
        );
        try {
            // 📡 PUBLICA NA FILA
            rabbitTemplate.convertAndSend(exchangeName, routingKey, envelope);
            log.info("Evento {} publicado no RabbitMQ ({}) pós-commit", eventType, routingKey);
            return;
        } catch (Exception amqpEx) {
            log.warn("Broker AMQP indisponível; acionando fallback HTTP...");
        }
    }
    
    // Fallback HTTP com Circuit Breaker
    notificationHttpFallbackClient.sendHttp(userId, recipientEmail, eventType, message);
}
```

**Routing Keys Publicadas:**
- `notification.registration.inscrition_confirmed`
- `notification.registration.inscrition_waitlist`
- `notification.registration.inscrition_cancelled`
- `notification.registration.payment_pending`
- etc.

**Quando é Publicado:**
- ✅ Após inscrição confirmada (CONFIRMED)
- ✅ Após inscrição em lista de espera (WAITLIST)
- ✅ Após cancelamento
- ✅ Após expiração de pagamento pendente

---

### 3.2 `event-service` — Publica Eventos de Eventos/Atividades

**Arquivo Principal:** 📁 `services/event-service/src/main/java/com/eventhub/event/service/NotificationClient.java`

**Estrutura Idêntica a `registration-service`:**

```java
private void dispatchNotification(Long userId, String recipientEmail, String eventType, String message) {
    // ... mesmo padrão AFTER_COMMIT ...
    
    if (messagingEnabled) {
        String routingKey = "notification.event." + eventType.toLowerCase();
        Map<String, Object> envelope = Map.of(...);
        try {
            // 📡 PUBLICA NA FILA
            rabbitTemplate.convertAndSend(exchangeName, routingKey, envelope);
            log.info("Evento {} publicado no RabbitMQ ({})", eventType, routingKey);
            return;
        } catch (Exception amqpEx) { ... }
    }
    
    notificationHttpFallbackClient.sendHttp(...);
}
```

**Routing Keys Publicadas:**
- `notification.event.activity_created`
- `notification.event.event_published`
- `notification.event.check_in_registered`
- `notification.event.certificate_issued`
- etc.

---

### 3.3 `payment-service` — Publica Eventos de Pagamento

**Arquivo Principal:** 📁 `services/payment-service/app/services/downstream_client.py`

**Método Chave:** `send_notification()`

```python
@classmethod
def send_notification(cls, user_id: int, recipient_email: str, event_type: str, message: str) -> bool:
    payload = {
        "userId": user_id,
        "recipientEmail": recipient_email,
        "eventType": event_type,
        "message": message,
    }
    
    if settings.messaging_enabled:
        routing_key = f"notification.payment.{event_type.lower()}"
        try:
            # 📡 PUBLICA NA FILA
            cls._publish_amqp(routing_key, payload)
            logger.info("Evento %s publicado via RabbitMQ (%s)", event_type, routing_key)
            return True
        except Exception as amqp_exc:
            logger.warning("Broker AMQP indisponível; acionando fallback HTTP...")
    
    # Fallback HTTP com Circuit Breaker + Retry (PyBreaker + Tenacity)
    url = f"{settings.notification_service_url}/api/notifications/send"
    try:
        notification_cb.call(cls._post_once, url, payload)
        return True
    except Exception as exc:
        logger.warning("Falha ao enviar notificação: %s", exc)
        return False
```

**Método Auxiliar:**

```python
@staticmethod
def _publish_amqp(routing_key: str, payload: Dict[str, Any]) -> None:
    credentials = pika.PlainCredentials(settings.rabbitmq_user, settings.rabbitmq_password)
    params = pika.ConnectionParameters(
        host=settings.rabbitmq_host,
        port=settings.rabbitmq_port,
        credentials=credentials,
        socket_timeout=1.0,
    )
    connection = pika.BlockingConnection(params)
    try:
        channel = connection.channel()
        channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type="topic", durable=True)
        
        envelope = {
            "eventId": str(uuid.uuid4()),
            "occurredAt": datetime.now(timezone.utc).isoformat(),
            "sourceService": settings.service_name,
            "routingKey": routing_key,
            "payload": payload,
        }
        
        # 📡 PUBLICA NA FILA
        channel.basic_publish(
            exchange=EXCHANGE_NAME,
            routing_key=routing_key,
            body=json.dumps(envelope).encode("utf-8"),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,  # Persistente
            ),
        )
    finally:
        if connection.is_open:
            connection.close()
```

**Routing Keys Publicadas:**
- `notification.payment.payment_pending`
- `notification.payment.payment_confirmed`
- `notification.payment.payment_refused`
- `notification.payment.payment_expired`

---

## 4. Consumidor (Subscriber) de Mensagens

### 4.1 `notification-service` — Consome Todas as Notificações

**Arquivo Principal:** 📁 `services/notification-service/app/services/amqp_consumer.py`

**Classe:** `NotificationAmqpConsumer`

```python
class NotificationAmqpConsumer:
    """Consumidor assíncrono RabbitMQ com suporte a Dead Letter Queue (DLQ)."""

    async def _run_loop(self) -> None:
        while not self._stopping.is_set():
            try:
                # Conecta ao RabbitMQ
                self._connection = await aio_pika.connect_robust(settings.amqp_url)
                channel = await self._connection.channel()
                await channel.set_qos(prefetch_count=10)

                # Declara exchanges
                domain_exchange = await channel.declare_exchange(
                    EXCHANGE_NAME, ExchangeType.TOPIC, durable=True
                )
                dlx_exchange = await channel.declare_exchange(
                    DLX_NAME, ExchangeType.TOPIC, durable=True
                )

                # Declara DLQ
                dlq = await channel.declare_queue(DLQ_NAME, durable=True)
                await dlq.bind(dlx_exchange, routing_key="#")

                # Declara fila principal com x-dead-letter-exchange
                queue = await channel.declare_queue(
                    QUEUE_NAME,
                    durable=True,
                    arguments={"x-dead-letter-exchange": DLX_NAME},
                )
                
                # Bind para múltiplos padrões de routing
                for key in ("notification.#", "event.#", "registration.#", "payment.#"):
                    await queue.bind(domain_exchange, routing_key=key)

                # 📡 CONSOME MENSAGENS
                await queue.consume(self._on_message)
                logger.info("Consumidor AMQP ativo na fila '%s'", QUEUE_NAME)
                
                await self._stopping.wait()
            except Exception as exc:
                logger.warning("Conexão AMQP indisponível (%s). Nova tentativa em 3s...", exc)
                await asyncio.sleep(3)

    @staticmethod
    async def _on_message(message: AbstractIncomingMessage) -> None:
        # Simula falha (RN05) se ativada
        if NotificationService.get_failure_state():
            logger.warning("Simulação de falha ativa: rejeitando mensagem para DLQ...")
            await message.reject(requeue=False)  # → vai para DLQ
            return

        try:
            raw = json.loads(message.body.decode("utf-8"))
            payload_data = raw.get("payload")
            req = NotificationSendRequest(**payload_data)

            db = SessionLocal()
            try:
                service = NotificationService(db)
                # ✅ PROCESSA A NOTIFICAÇÃO
                service.send_notification(req)
            finally:
                db.close()

            # ✅ CONFIRMA O PROCESSAMENTO
            await message.ack()
            logger.info("Notificação AMQP processada: eventType=%s, userId=%s", req.eventType, req.userId)
        except Exception as exc:
            logger.error("Erro ao processar: enviando para DLQ.")
            await message.reject(requeue=False)  # → vai para DLQ
```

**Inicialização (Lifespan Event):**

- 📁 `services/notification-service/app/main.py`

```python
@app.lifespan
async def lifespan(app: FastAPI):
    # Inicia consumidor ao ligar a aplicação
    await amqp_consumer.start()
    logger.info("AMQP consumer iniciado")
    yield
    # Para consumidor ao desligar
    await amqp_consumer.stop()
    logger.info("AMQP consumer parado")
```

---

## 5. Padrão de Implementação: AFTER_COMMIT + Dual-Mode Fallback

### 5.1 Flow em Tempo Real

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Inscrição confirmada (registration-service)              │
│                                                             │
│   ✅ INSERT registration (CONFIRMED)                        │
│   ✅ COMMIT TRANSACTION                                     │
│   └─→ AFTER_COMMIT callback registrado                     │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Callback AFTER_COMMIT executado (lock já liberado)      │
│                                                             │
│   dispatchNotification(...) {                              │
│       if (messagingEnabled) {                              │
│           rabbitTemplate.convertAndSend(...)  ◄─── 📡 RabbitMQ
│       }                                                     │
│   }                                                         │
└─────────────────────────────────────────────────────────────┘
                          │
         ┌────────────────┼────────────────┐
         │                                 │
         ▼                                 ▼
    ✅ RabbitMQ OK                   ❌ RabbitMQ DOWN
    └─→ Enfileira                    └─→ Fallback HTTP
                                         └─→ CircuitBreaker
                                             └─→ NotificationService
```

### 5.2 Dual-Mode: RabbitMQ + Fallback HTTP

Cada publicador (Java/Python) implementa:

```
1. Tenta enviar via RabbitMQ
   └─ Sucesso? ✅ Retorna
   └─ Falha?   ❌ Vai para (2)

2. Fallback HTTP com proteção por Circuit Breaker:
   └─ Java:   Resilience4j
   └─ Python: PyBreaker + Tenacity
   └─ Falha após tentativas?
      └─ Log de erro
      └─ NÃO propaga (degradação graciosa, RN05)
```

---

## 6. Cenários de Teste — Chaos Lab

### 6.1 RN05 — Falha de Notificação

**Arquivo:** 📁 `web-app/js/lab-runner.js` (linhas 142-180)

```javascript
async function runNotificationFailure({ token, eventId, onLog }) {
    // Ativa falha simulada
    await call('/api/notifications/simulate-failure?fail=true', { method: 'POST' });
    
    try {
        // Tenta inscrição durante falha
        const registration = await call('/api/registrations', {
            method: 'POST',
            token: participant.token,
            body: { activityId: activity.id }
        });
        
        // Validação: inscrição deve ser CONFIRMED mesmo com notificação fora
        check(
            'RN05 - Inscrição concluída mesmo com falha',
            registration.ok && registration.data.status === 'CONFIRMED'
        );
    } finally {
        // Desativa falha simulada
        await call('/api/notifications/simulate-failure?fail=false', { method: 'POST' });
    }
}
```

**Endpoint Simulador de Falha:**

- 📁 `services/notification-service/app/main.py`

```python
@router.post("/simulate-failure")
async def simulate_failure(fail: bool = Query(False)):
    """Simula indisponibilidade (HTTP 503) do serviço."""
    NotificationService.set_failure_state(fail)
    return {"simulating_failure": fail}
```

**Efeito no Consumidor:**

Quando `simulate_failure=true`:
1. Consumidor recebe a mensagem da fila
2. Valida `NotificationService.get_failure_state()` → **true**
3. Rejeita com `requeue=False` (não recoloca na fila)
4. Mensagem vai automaticamente para **DLQ** (`eventhub.notifications.dlq`)
5. Entretanto, a inscrição original em `registration-service` **já foi commitada** e portanto está **CONFIRMED**

---

## 7. Resumo de Localização dos Códigos

| Componente | Arquivo | Tipo | Descrição |
|---|---|---|---|
| **Configuração RabbitMQ (Java)** | `services/registration-service/src/.../config/RabbitMqConfig.java` | Exchange + Template | Declara exchange `eventhub.domain.events` e `RabbitTemplate` |
| **Configuração RabbitMQ (Java)** | `services/event-service/src/.../config/RabbitMqConfig.java` | Exchange + Template | Idêntico ao registration-service |
| **Publisher (Registration)** | `services/registration-service/src/.../service/ExternalIntegrationService.java` | Publicador | `notifyUser()` → `dispatchNotification()` com AFTER_COMMIT |
| **Publisher (Event)** | `services/event-service/src/.../service/NotificationClient.java` | Publicador | `notifyUser()` → `dispatchNotification()` com AFTER_COMMIT |
| **Publisher (Payment)** | `services/payment-service/app/services/downstream_client.py` | Publicador | `send_notification()` + `_publish_amqp()` |
| **Consumer** | `services/notification-service/app/services/amqp_consumer.py` | Consumidor | `NotificationAmqpConsumer` com DLQ + simulação de falha |
| **Inicialização Consumer** | `services/notification-service/app/main.py` | Lifespan | Inicia/para consumidor na inicialização |
| **Testes de Caos** | `web-app/js/lab-runner.js` | Teste | `runNotificationFailure()` ativa falha simulada |
| **Simulador de Falha** | `services/notification-service/app/main.py` | Endpoint | `/api/notifications/simulate-failure` |
| **Docker Compose** | `docker-compose.yml` | Infraestrutura | Serviço RabbitMQ 3.13 Management |

---

## 8. Comandos Úteis para Inspeção

### 8.1 Acessar Management UI do RabbitMQ

```
http://localhost:15672
Usuário: eventhub
Senha: eventhub_secret_2026
```

**Abas relevantes:**
- **Exchanges** → Ver `eventhub.domain.events`
- **Queues** → Ver `eventhub.notifications.queue` (mensagens não confirmadas)
- **Queues** → Ver `eventhub.notifications.dlq` (mensagens rejeitadas)

### 8.2 Verificar Logs do Consumidor

```bash
docker logs eventhub-notification-service -f
# Procure por:
# - "Consumidor AMQP ativo na fila"
# - "Notificação AMQP processada"
# - "Simulação de falha ativa"
```

### 8.3 Verificar Produção de Eventos

```bash
docker logs eventhub-registration-service -f
# Procure por:
# - "Evento REGISTRATION_CONFIRMED publicado no RabbitMQ"
```

---

## 9. Propriedades de Configuração

### 9.1 `application.yml` (Java Services)

```yaml
eventhub:
  messaging:
    enabled: true                      # Ativa/desativa RabbitMQ
    exchange: eventhub.domain.events   # Nome do exchange
  downstream:
    payment-service-url: http://payment-service:8085
    notification-service-url: http://notification-service:8086
```

### 9.2 `.env` (Python Services)

```bash
MESSAGING_ENABLED=true
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=eventhub
RABBITMQ_PASSWORD=eventhub_secret_2026
```

---

## 10. Conclusão

A fila de mensageria **RabbitMQ** é o **coração da desacoplamento assíncrono** no EventHub:

✅ **Produtores** publicam eventos de domínio pós-commit (lock liberado)  
✅ **Consumidor** processa notificações assincronamente  
✅ **DLQ** retém mensagens rejeitadas (zero perda)  
✅ **Fallback HTTP** garante entrega mesmo com broker indisponível (RN05)  
✅ **Teste de Caos** valida resiliência via simulação de falha
