# EventHub — Casos de Teste por Serviço

Este documento descreve, passo a passo, como testar cada microsserviço pela aplicação web (SPA) em `http://localhost:8080`. Quando uma funcionalidade não tem tela própria na SPA, o caso indica a alternativa via **Swagger** (`http://localhost:8080/swagger.html`) ou via **Chaos Lab**.

---

## 0. Pré-requisitos

1. Subir o ambiente na raiz do projeto:
   ```bash
   docker compose up -d --build
   ```
2. Confirmar que todos os containers estão `healthy`:
   ```bash
   docker compose ps
   ```
3. Abrir `http://localhost:8080` no navegador.

### Contas de teste (senha `123456`)

| Perfil | E-mail | Uso nos casos |
|---|---|---|
| Organizador | `organizador@eventhub.com` | Eventos, atividades, check-in, relatórios, Chaos Lab |
| Participante | `ana@eventhub.com` | Inscrições, pagamentos, certificados |
| Participante | `bruno@eventhub.com` | Teste de isolamento entre usuários |

Para o acesso ao Swagger, faça `POST /api/users/login` com uma das contas, copie o `token` e clique em **Authorize**.

---

## 1. `user-service` (Java, porta 8081)

### CT-US-01 — Login com credenciais válidas
1. Na tela de login, digite `organizador@eventhub.com` e `123456`.
2. Clique em **Entrar**.

**Resultado esperado:** o sistema abre a área do organizador, com o menu *Meus Eventos*, *Novo Evento*, *Credenciamento & Check-in* e *Relatórios & Indicadores*.

### CT-US-02 — Login com senha incorreta
1. Digite `organizador@eventhub.com` e a senha `000000`.
2. Clique em **Entrar**.

**Resultado esperado:** o login é recusado e nenhuma área é liberada.

### CT-US-03 — Cadastro de nova conta
1. Na tela de login, clique no link de criação de conta.
2. Preencha **Nome**, **E-mail**, **Senha** (mínimo 6 caracteres), **Perfil de Acesso** (*Participante*) e, se quiser, **Instituição**.
3. Clique em **Concluir Cadastro**.

**Resultado esperado:** a conta é criada e o usuário entra com o perfil *Participante*, com o menu *Explorar Eventos*, *Minhas Inscrições*, *Meus Pagamentos* e *Meus Certificados*.

### CT-US-04 — Cadastro com e-mail já existente
1. Repita o cadastro usando `ana@eventhub.com`.

**Resultado esperado:** o cadastro é recusado com mensagem de erro, sem criar outra conta.

### CT-US-05 — Atualização do perfil
1. Logado como participante, abra **Meu Perfil**.
2. Altere o campo **Nome** e clique em salvar.
3. Recarregue a página.

**Resultado esperado:** o novo nome aparece no cabeçalho e permanece após o recarregamento.

### CT-US-06 — Isolamento de acesso entre usuários
1. Logado como organizador, abra **Validadores do Sistema**.
2. No validador de isolamento, informe o ID de um usuário diferente do logado (por exemplo, o de `bruno@eventhub.com`, que é `3`) e execute o teste.

**Resultado esperado:** o resultado mostra *Acesso Bloqueado pelo Serviço de Usuários* para o acesso indevido.

---

## 2. `event-service` (Java, porta 8082)

### CT-EV-01 — Criar evento
1. Logado como organizador, clique em **Novo Evento**.
2. Preencha **Título**, **Organização**, **Data de Início**, **Data de Término** e **Descrição**.
3. Clique em **Cadastrar Novo Evento**.

**Resultado esperado:** a mensagem *Novo evento cadastrado!* aparece e o evento entra em *Meus Eventos* com status **Rascunho**.

### CT-EV-02 — Editar evento
1. Em *Meus Eventos*, clique no ícone de edição de um evento em rascunho.
2. Altere o **Título** e salve.

**Resultado esperado:** a mensagem *Evento atualizado com sucesso!* aparece e o novo título é exibido na lista.

### CT-EV-03 — Cadastrar atividade com preço e lista de espera
1. Em *Meus Eventos*, clique em **Programação** no evento.
2. Clique em **Nova Atividade**.
3. Preencha **Título**, **Palestrante**, **Local**, **Início**, **Término**, **Capacidade** e **Valor de Inscrição** (use um valor maior que zero para testar atividade paga).
4. Ative **Permitir Lista de Espera**.
5. Clique em **Cadastrar Atividade**.

**Resultado esperado:** a atividade aparece na tabela da programação com a coluna **Ocupação** zerada e a coluna **Lista de Espera** indicando a configuração escolhida.

### CT-EV-04 — Publicar evento
1. Em *Meus Eventos*, clique em **Publicar** no evento em rascunho.

**Resultado esperado:** o status muda para **Publicado** e o evento passa a aparecer na busca do participante (`search-service`).

### CT-EV-05 — Cancelar evento
1. Em *Meus Eventos*, clique em **Cancelar** em um evento de teste.

**Resultado esperado:** o status muda para **Cancelado** e o evento deixa de aparecer na busca pública.

### CT-EV-06 — Marcar atividade como concluída
1. Abra a **Programação** de um evento publicado.
2. Clique em **Marcar Concluída** em uma atividade.

**Resultado esperado:** a atividade passa a exibir **Concluída**.

### CT-EV-07 — Check-in de participante
Pré-condição: existe uma inscrição com status `CONFIRMED` (ver CT-RG-01 ou o Chaos Lab).
1. Logado como organizador, abra **Credenciamento & Check-in**.
2. No campo **ID da inscrição**, digite o ID da inscrição confirmada.
3. Clique em **Registrar presença** (ou pressione Enter).

**Resultado esperado:** o registro aparece em *Últimos registros nesta sessão*. Um segundo check-in da mesma inscrição deve ser recusado.

### CT-EV-08 — Emissão de certificado (via Swagger)
Pré-condição: a inscrição do participante tem presença registrada (CT-EV-07) e a atividade foi concluída (CT-EV-06).
1. Abra `http://localhost:8080/swagger.html` e selecione **event-service**.
2. Autentique-se com o token do participante.
3. Execute `POST /api/events/certificates/issue` com o `activityId` da atividade concluída.
4. Abra a SPA como o mesmo participante e vá em **Meus Certificados**.

**Resultado esperado:** a resposta do Swagger é de criação, e o certificado aparece em *Meus Certificados* com o título da atividade.

### CT-EV-09 — Emissão de certificado sem presença registrada
1. Repita CT-EV-08 usando uma inscrição sem check-in.

**Resultado esperado:** a emissão é recusada e nenhum certificado é criado.

### CT-EV-10 — Validação pública de certificado
1. Em **Meus Certificados**, copie o código de validação de um certificado.
2. Em **Validar um certificado**, cole o código e clique em **Validar**.
3. Repita com um código inventado, por exemplo `CODIGO-INEXISTENTE`.

**Resultado esperado:** o primeiro mostra *Certificado válido*. O segundo mostra *Certificado não encontrado ou inválido*.

---

## 3. `search-service` (Python, porta 8083)

### CT-BU-01 — Listar eventos publicados
1. Logado como participante, abra **Explorar Eventos**.

**Resultado esperado:** somente eventos com status **Publicado** aparecem. Eventos em rascunho ou cancelados não aparecem.

### CT-BU-02 — Busca por palavra-chave
1. Digite um termo do título de um evento no campo de busca.

**Resultado esperado:** a vitrine mostra apenas os eventos que contêm o termo.

### CT-BU-03 — Filtro por preço
1. Em **Modalidade de Inscrição**, selecione *Gratuitos*. Depois, selecione *Pagos*.

**Resultado esperado:** a lista muda conforme a seleção.

### CT-BU-04 — Filtro somente com vagas
1. Ative **Somente com vagas**.

**Resultado esperado:** atividades esgotadas deixam de aparecer.

### CT-BU-05 — Detalhe do evento e vagas em tempo real
1. Clique em um evento da vitrine.
2. Confira a seção **Programação de Atividades e Disponibilidade**.
3. Compare o texto *N vaga(s) disponível(is)* com a barra **Ocupação da Atividade** (`ocupado / capacidade`).

**Resultado esperado:** os valores são coerentes entre si. Após uma inscrição nova (CT-RG-01), o saldo diminui.

---

## 4. `registration-service` (Java, porta 8084)

### CT-RG-01 — Criar inscrição (via Chaos Lab ou Swagger)
A SPA não tem botão de inscrição. Use uma das alternativas:
- **Chaos Lab**: o cenário de concorrência cria inscrições reais (ver CT-LAB-01).
- **Swagger**: com o token do participante, execute `POST /api/registrations` informando o `activityId` de uma atividade com vagas.

**Resultado esperado:** a inscrição é criada com um ID. Em atividade gratuita, o status vai para `CONFIRMED`. Em atividade paga, vai para `PENDING_PAYMENT`.

### CT-RG-02 — Listar minhas inscrições
1. Logado como participante, abra **Minhas Inscrições**.
2. Clique em **Atualizar** se necessário.

**Resultado esperado:** cada inscrição aparece com o título da atividade e o identificador *Inscrição #ID*.

### CT-RG-03 — Cancelar inscrição
1. Em **Minhas Inscrições**, clique em **Cancelar inscrição** em uma inscrição ativa.
2. Confirme no diálogo.

**Resultado esperado:** a inscrição muda para cancelada. Se houver lista de espera, o próximo da fila é promovido (confira em **Minhas Inscrições** do outro participante).

### CT-RG-04 — Cancelamento de inscrição alheia (via Swagger)
1. Com o token de `bruno@eventhub.com`, execute `PATCH /api/registrations/{registrationId}/cancel` usando o ID de uma inscrição de `ana@eventhub.com`.

**Resultado esperado:** o cancelamento é recusado e a inscrição permanece ativa.

### CT-RG-05 — Relatório gerencial do organizador
1. Logado como organizador, abra **Relatórios & Indicadores**.
2. Selecione um evento no seletor.

**Resultado esperado:** os cartões mostram totais de inscrições, confirmadas, pendentes de pagamento, lista de espera, canceladas, presenças e receita. A seção **Ocupação por atividade** mostra uma linha por atividade.

### CT-RG-06 — Relatório de evento de outro organizador
1. Com o token de um organizador que não criou o evento (crie um segundo organizador pelo cadastro, se necessário), execute `GET /api/registrations/reports/event/{eventId}` pelo Swagger.

**Resultado esperado:** o acesso é recusado.

---

## 5. `payment-service` (Python, porta 8085)

Pré-condição: existe uma inscrição em atividade **paga** com status `PENDING_PAYMENT`.

### CT-PG-01 — Visualizar cobrança pendente
1. Logado como participante, abra **Meus Pagamentos**.
2. Clique em **Atualizar** se necessário.

**Resultado esperado:** a cobrança aparece com os botões **Aprovar**, **Recusar** e **Expirar**.

### CT-PG-02 — Aprovar pagamento
1. Clique em **Aprovar**.

**Resultado esperado:** o pagamento fica `APPROVED` e a inscrição vai para `CONFIRMED` (confira em **Minhas Inscrições**).

### CT-PG-03 — Recusar pagamento (RN03)
1. Crie outra inscrição paga e abra **Meus Pagamentos**.
2. Clique em **Recusar**.

**Resultado esperado:** o pagamento fica `REFUSED`, a vaga é liberada e a inscrição deixa de ocupar a atividade. O saldo de vagas em **Explorar Eventos** aumenta.

### CT-PG-04 — Expirar pagamento (RN03)
1. Crie outra inscrição paga e abra **Meus Pagamentos**.
2. Clique em **Expirar**.

**Resultado esperado:** o pagamento fica `EXPIRED` e a vaga é liberada, como no caso anterior.

### CT-PG-05 — Pagamento de inscrição já aprovada
1. Em **Meus Pagamentos**, tente **Recusar** um pagamento já `APPROVED`. Caso os botões não estejam disponíveis, use o Swagger com `POST /api/payments/{paymentId}/refuse`.

**Resultado esperado:** a transição é recusada e o status `APPROVED` é mantido.

---

## 6. `notification-service` (Python, porta 8086)

### CT-NT-01 — Enviar notificação de teste
1. Logado como organizador, abra **Validadores do Sistema**.
2. Na seção de notificações, clique em **Disparar Notificação de Teste Agora**.

**Resultado esperado:** a resposta mostra **HTTP 200** e a mensagem *Notificação Entregue e Persistida*.

### CT-NT-02 — Caixa de notificações
1. Clique no sino de notificações no cabeçalho.
2. Marque uma notificação como lida.
3. Clique em apagar as notificações lidas.

**Resultado esperado:** a notificação lida perde o destaque, e as apagadas somem da lista.

### CT-NT-03 — Simular falha do serviço (RN05)
1. Em **Validadores do Sistema**, ative a falha simulada da seção de notificações.
2. Confirme o estado *Falha Simulada ATIVA (503)*.
3. Clique em **Disparar Notificação de Teste Agora**.

**Resultado esperado:** a resposta mostra **HTTP 503** e a mensagem *Serviço em Falha Simulada (503 Service Unavailable)*.

### CT-NT-04 — Fluxos críticos com notificação em falha (RN05)
Pré-condição: falha simulada ativa (CT-NT-03).
1. Como participante, cancele uma inscrição (CT-RG-03) ou aprove um pagamento (CT-PG-02).

**Resultado esperado:** a operação conclui normalmente, mesmo com o serviço de notificações indisponível.

### CT-NT-05 — Desativar a falha simulada
1. Desative a falha simulada e confirme o estado *Serviço Operacional (200)*.
2. Dispare a notificação de teste novamente.

**Resultado esperado:** HTTP 200 e notificação entregue.

---

## 7. Status dos Serviços

### CT-ST-01 — Monitor de saúde
1. Logado como organizador, abra **Status dos Serviços**.
2. Clique em **Atualizar Status**.

**Resultado esperado:** o cabeçalho mostra **6 de 6 serviços ativos**, com cada serviço listado como disponível.

### CT-ST-02 — Detecção de serviço indisponível
1. Pare um serviço com `docker compose stop search-service`.
2. Na tela, clique em **Atualizar Status**.
3. Reinicie com `docker compose start search-service`.

**Resultado esperado:** o serviço aparece como indisponível enquanto estiver parado e volta a ficar ativo após o reinício.

---

## 8. Chaos Lab (validação arquitetural)

Acesse como `organizador@eventhub.com`, em **⚡ Validação Arquitetural & Chaos Lab**, e clique em **Executar laboratório**.

### CT-LAB-01 — Concorrência de vagas (RN01)
1. Execute o laboratório.
2. Confira o card *Concorrência (RN01)*, que mostra as inscrições simultâneas e as vagas.

**Resultado esperado:** o número de inscrições confirmadas não ultrapassa a capacidade. Não há *overbooking*.

### CT-LAB-02 — Falha de notificação (RN05)
1. Execute o laboratório e observe a verificação de falha de notificação.

**Resultado esperado:** o laboratório mostra que a falha do serviço de notificações não derruba o fluxo de inscrição.

### CT-LAB-03 — Recusa de pagamento (RN03)
1. Execute o laboratório e observe a verificação de recusa de pagamento.

**Resultado esperado:** a vaga é liberada após a recusa, e o registro mostra a sequência de eventos.

### CT-LAB-04 — Registro do laboratório
1. Após a execução, confira a seção **Registro do laboratório** e a tabela de requisições.

**Resultado esperado:** cada requisição mostra o status HTTP e a latência em milissegundos.

---

## 9. Fluxo ponta a ponta (recomendado como teste final)

1. **Organizador** cria um evento (CT-EV-01), adiciona uma atividade gratuita e uma paga (CT-EV-03) e publica o evento (CT-EV-04).
2. **Participante** cadastra-se (CT-US-03) e encontra o evento em **Explorar Eventos** (CT-BU-01).
3. **Participante** cria inscrições pelo Swagger (CT-RG-01). A inscrição paga fica `PENDING_PAYMENT`.
4. **Participante** aprova o pagamento (CT-PG-02).
5. **Organizador** registra a presença (CT-EV-07) e marca a atividade como concluída (CT-EV-06).
6. **Participante** recebe o certificado via Swagger (CT-EV-08) e valida-o (CT-EV-10).
7. **Organizador** confere os números em **Relatórios & Indicadores** (CT-RG-05).

---

## Resumo de cobertura

| Serviço | Casos | Como testar |
|---|---|---|
| `user-service` | CT-US-01 a CT-US-06 | SPA |
| `event-service` | CT-EV-01 a CT-EV-10 | SPA e Swagger (certificados) |
| `search-service` | CT-BU-01 a CT-BU-05 | SPA |
| `registration-service` | CT-RG-01 a CT-RG-06 | SPA e Swagger (criação e cancelamento alheio) |
| `payment-service` | CT-PG-01 a CT-PG-05 | SPA e Swagger (fallback) |
| `notification-service` | CT-NT-01 a CT-NT-05 | SPA |
| Status e infraestrutura | CT-ST-01 e CT-ST-02 | SPA e Docker |
| Chaos Lab (RN01, RN03, RN05) | CT-LAB-01 a CT-LAB-04 | SPA |
