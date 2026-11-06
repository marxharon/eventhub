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
1. Em *Meus Eventos*, clique em **Ver Programação** no evento.
2. Clique em **Nova Atividade**.
3. Preencha **Título**, **Palestrante**, **Local**, **Início**, **Término**, **Capacidade** e **Valor de Inscrição** (use um valor maior que zero para testar atividade paga).
4. Ative **Permitir Lista de Espera**.
5. Clique em **Cadastrar Atividade**.

**Resultado esperado:** a atividade aparece na tabela da programação com a coluna **Ocupação** zerada e a coluna **Lista de Espera** indicando a configuração escolhida.

### CT-EV-03B — Editar atividade da programação e validar RN01
1. Na tela **Programação** do evento, clique no ícone de lápis (**Editar Atividade**) ao lado de uma atividade existente.
2. Altere o **Local** ou o **Horário** e clique em **Salvar Alterações**.
3. Em uma atividade que já possua participantes inscritos (`occupiedSlots >= 2`), edite novamente e informe uma **Capacidade** menor que o número de inscritos ocupados.

**Resultado esperado:** na primeira edição, a atividade é atualizada (`200 OK`) e os participantes inscritos recebem a notificação `ACTIVITY_UPDATED`. Na segunda tentativa, o sistema recusa a redução com alerta da regra **RN01** (`HTTP 422 Unprocessable Entity`).

### CT-EV-04 — Publicar evento
1. Em *Meus Eventos*, clique em **Publicar** no evento em rascunho.

**Resultado esperado:** o status muda para **Publicado** e o evento passa a aparecer na busca do participante (`search-service`).

### CT-EV-05 — Cancelar evento (encerramento de reservas e notificação)
1. Em *Meus Eventos*, clique em **Cancelar** em um evento de teste que possua inscrições ativas.

**Resultado esperado:** o status do evento muda para **Cancelado**, o evento deixa de aparecer na busca pública, todas as inscrições ativas (`CONFIRMED`, `PENDING_PAYMENT`, `WAITLIST`) passam para `CANCELLED`, eventuais pagamentos `PENDING` passam para `EXPIRED`, `occupiedSlots` das atividades zera e os participantes inscritos recebem a notificação `EVENT_CANCELLED`.

### CT-EV-06 — Marcar atividade como concluída
1. Abra a **Programação** de um evento publicado.
2. Clique em **Marcar Concluída** em uma atividade.

**Resultado esperado:** a atividade passa a exibir **Concluída** (e emite automaticamente certificados para os participantes que já realizaram check-in).

### CT-EV-07 — Check-in de participante (RF09)
Pré-condição: existe uma inscrição com status `CONFIRMED` (ver CT-RG-01).
1. Logado como organizador, abra **Credenciamento & Check-in**.
2. Selecione o **Evento** no seletor superior (e filtre pelo nome/e-mail se desejar).
3. Na linha do participante inscrito, clique em **Registrar presença**.

**Resultado esperado:** a linha passa a exibir o badge **Presente**, o botão muda para *Presença registrada* (desabilitado) e o registro aparece em *Últimos registros nesta sessão*. Uma segunda tentativa via API/Swagger para a mesma inscrição retorna `409 Conflict` (`RF09: Check-in já realizado`).

### CT-EV-08 — Emissão de certificado pela SPA (RF10, RN06)
Pré-condição: a inscrição do participante tem presença registrada (CT-EV-07) e a atividade foi concluída (CT-EV-06).
1. Logado como o participante, abra **Minhas Inscrições** ou **Meus Certificados**.
2. Clique no botão **Emitir certificado** da atividade concluída (ou confira a seção de certificados emitidos automaticamente na conclusão/check-in).

**Resultado esperado:** o certificado é emitido (`201 Created`) ou, se já tiver sido gerado automaticamente na conclusão da atividade, consta listado em **Meus Certificados** com código de autenticidade único (`validationCode`).

### CT-EV-09 — Emissão de certificado sem conclusão ou sem presença (RN06)
1. Logado como participante, vá em **Minhas Inscrições** (ou **Meus Certificados**) e clique em **Emitir certificado** em uma atividade ainda não concluída ou na qual o participante não realizou check-in.

**Resultado esperado:** o sistema exibe o aviso **Emissão não permitida (RN06)** (`HTTP 422`) informando que a atividade ainda não foi concluída ou que não há registro de presença.

### CT-EV-10 — Validação pública de certificado
1. Em **Meus Certificados**, copie o código de validação de um certificado.
2. Em **Validar um certificado**, cole o código e clique em **Validar**.
3. Repita com um código inventado, por exemplo `CODIGO-INEXISTENTE`.

**Resultado esperado:** o primeiro mostra *Certificado válido* (`HTTP 200`). O segundo mostra *Certificado não encontrado ou inválido* (`HTTP 404`).

---

## 3. `search-service` (Python, porta 8083)

### CT-BU-01 — Listar eventos publicados
1. Logado como participante, abra **Explorar Eventos**.

**Resultado esperado:** somente eventos com status **Publicado** aparecem. Eventos em rascunho ou cancelados não aparecem.

### CT-BU-02 — Busca por palavra-chave e filtro por organização
1. Digite um termo no campo **Palavra-chave** e/ou informe a instituição no campo **Organização** (ex.: `SBC`) e clique em **Buscar**.

**Resultado esperado:** a vitrine mostra apenas os eventos publicados que atendem simultaneamente à palavra-chave e à organização filtrada.

### CT-BU-03 — Filtro por modalidade (gratuitas / pagas)
1. Em **Modalidade de Inscrição**, selecione *Apenas gratuitas*. Depois, selecione *Apenas pagas*.

**Resultado esperado:** a listagem de eventos/atividades filtra apenas atividades gratuitas ou pagas conforme a seleção.

### CT-BU-04 — Filtro somente com vagas (descontando `PENDING_PAYMENT`)
1. Ative o seletor **Com vagas**.

**Resultado esperado:** atividades cuja soma de vagas confirmadas (`occupied_slots`) + reservas aguardando pagamento (`PENDING_PAYMENT`) já atingiu a capacidade deixam de aparecer.

### CT-BU-05 — Detalhe do evento e vagas em tempo real
1. Clique em **Ver Programação** em um evento da vitrine.
2. Confira a seção **Programação de Atividades e Disponibilidade**.
3. Compare o badge *N vaga(s) disponível(is)* com a barra **Ocupação da Atividade** (`ocupado / capacidade`).

**Resultado esperado:** os valores são coerentes entre si e são atualizados imediatamente na própria tela assim que o participante realiza uma inscrição (mesmo quando a inscrição paga entra em `PENDING_PAYMENT`).

### CT-BU-06 — Busca direta de atividades da programação
1. Em **Explorar Eventos**, clique na aba **Atividades da Programação**.
2. Digite o nome de um palestrante (ex.: `Helena`) ou local no campo **Palavra-chave** e clique em **Buscar**.

**Resultado esperado:** a tela consulta `GET /api/search/activities` e exibe diretamente os cards das atividades correspondentes, permitindo inscrever-se ou entrar na lista de espera sem precisar abrir o evento primeiro.

---

## 4. `registration-service` (Java, porta 8084)

### CT-RG-01 — Criar inscrição e entrar na Lista de Espera pela SPA (RF05, RF06, RN04)
1. Logado como participante, abra **Explorar Eventos**, clique em **Ver Programação** de um evento (ou use a aba **Atividades da Programação**).
2. Em uma atividade com vagas disponíveis, clique em **Inscrever-se nesta atividade** e confirme.
3. Em uma atividade com vagas esgotadas (`0 vagas`) e lista de espera habilitada, verifique que o botão exibe **Entrar na Lista de Espera**, clique nele e confirme.

**Resultado esperado:**
- Em atividade gratuita com vaga: inscrição criada como `CONFIRMED` e contador de vagas atualizado imediatamente na tela.
- Em atividade paga com vaga: inscrição criada como `PENDING_PAYMENT` (vaga já reservada no saldo público) e cobrança gerada em **Meus Pagamentos**.
- Em atividade lotada com lista de espera: inscrição criada como `WAITLIST`.
- *(Via Swagger)* Tentar `POST /api/registrations` em atividade de evento `DRAFT` ou `CANCELLED` retorna `409 Conflict`.

### CT-RG-02 — Listar minhas inscrições e consultar detalhes individuais (`GET /api/registrations/{id}`)
1. Logado como participante, abra **Minhas Inscrições**.
2. Clique no botão **Detalhes** de uma inscrição.

**Resultado esperado:** cada inscrição aparece com o título da atividade, status e número. Ao clicar em **Detalhes**, abre-se um modal com os dados retornados por `GET /api/registrations/{registrationId}` (ID, atividade, status, data de criação, atualização e expiração).

### CT-RG-03 — Cancelar inscrição, promoção da fila e notificação (RN04, RF08)
1. Em **Minhas Inscrições**, clique em **Cancelar inscrição** em uma inscrição ativa (`CONFIRMED`, `PENDING_PAYMENT` ou `WAITLIST`) e confirme.

**Resultado esperado:** a inscrição muda para `CANCELLED`, o titular recebe a notificação `REGISTRATION_CANCELLED` no sino de notificações, eventual pagamento `PENDING` é encerrado como `EXPIRED` e, se havia participante em `WAITLIST`, o primeiro da fila é promovido automaticamente (`CONFIRMED` em atividade gratuita ou `PENDING_PAYMENT` em atividade paga, recebendo `WAITLIST_PROMOTED`).

### CT-RG-04 — Cancelamento ou consulta de inscrição alheia (RN07 via Swagger)
1. Com o token de `bruno@eventhub.com`, execute `GET /api/registrations/{registrationId}` ou `PATCH /api/registrations/{registrationId}/cancel` usando o ID de uma inscrição de `ana@eventhub.com`.

**Resultado esperado:** o acesso é recusado com `403 Forbidden` e a inscrição de Ana permanece inalterada.

### CT-RG-05 — Relatório gerencial do organizador (RF11)
1. Logado como organizador, abra **Relatórios & Indicadores**.
2. Selecione um evento no seletor.

**Resultado esperado:** os cartões e a tabela mostram totais consistentes de **Inscrições**, **Confirmadas**, **Aguard. pagamento**, **Lista de espera**, **Canceladas**, **Recus. / Expir.** (`refused` + `expired`), **Presenças** e **Receita**, além do gráfico **Ocupação por atividade**.

### CT-RG-06 — Relatório de evento de outro organizador (RN07)
1. Com o token de um participante ou de outro organizador que não criou o evento, execute `GET /api/registrations/reports/event/{eventId}` pelo Swagger.

**Resultado esperado:** o acesso é recusado com `403 Forbidden`.

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

1. **Organizador** cria um evento (CT-EV-01), adiciona uma atividade gratuita e uma paga (CT-EV-03), edita um detalhe da atividade (CT-EV-03B) e publica o evento (CT-EV-04).
2. **Participante** cadastra-se ou faz login (CT-US-01/03) e encontra o evento/atividade em **Explorar Eventos** usando filtro por organização ou a aba **Atividades da Programação** (CT-BU-02, CT-BU-06).
3. **Participante** inscreve-se diretamente pela SPA nas atividades (CT-RG-01). A inscrição paga fica `PENDING_PAYMENT` e já desconta 1 vaga no catálogo (CT-BU-04/05).
4. **Participante** aprova o pagamento em **Meus Pagamentos** (CT-PG-02).
5. **Organizador** registra a presença em **Credenciamento & Check-in** (CT-EV-07) e marca a atividade como concluída (CT-EV-06).
6. **Participante** consulta/emite o certificado em **Meus Certificados** (CT-EV-08) e valida o código publicamente (CT-EV-10).
7. **Organizador** confere os indicadores consolidados em **Relatórios & Indicadores** (CT-RG-05).

---

## Resumo de cobertura

| Serviço | Casos | Como testar |
|---|---|---|
| `user-service` | CT-US-01 a CT-US-06 | 100% via SPA |
| `event-service` | CT-EV-01 a CT-EV-10 | 100% via SPA |
| `search-service` | CT-BU-01 a CT-BU-06 | 100% via SPA |
| `registration-service` | CT-RG-01 a CT-RG-06 | SPA (fluxos funcionais) e Swagger (testes negativos de segurança CT-RG-04 e CT-RG-06) |
| `payment-service` | CT-PG-01 a CT-PG-05 | 100% via SPA (e Swagger para reenvio pós-aprovação) |
| `notification-service` | CT-NT-01 a CT-NT-05 | 100% via SPA |
| Status e infraestrutura | CT-ST-01 e CT-ST-02 | SPA e Docker |
| Chaos Lab (RN01, RN03, RN05) | CT-LAB-01 a CT-LAB-04 | 100% via SPA |
