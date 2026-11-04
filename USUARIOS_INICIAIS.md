# Credenciais de Acesso — Usuários Iniciais do EventHub

Este arquivo lista os usuários pré-cadastrados na carga inicial (*seed*) do banco de dados PostgreSQL ([infra/postgres/init.sql](infra/postgres/init.sql)) para acesso imediato na tela de login da aplicação (`http://localhost:8080`).

| ID | Nome Completo | E-mail de Acesso | Senha | Perfil (`Role`) | Organização / Instituição | Permissões de Menu |
|---|---|---|---|---|---|---|
| **1** | Prof. Carlos Mendes | `organizador@eventhub.com` | `123456` | `ORGANIZER` | SBC - Sociedade Brasileira de Computação | Meus Eventos, Novo Evento, Credenciamento & Check-in, Relatórios & Indicadores, Validadores do Sistema, Status dos Serviços, Meu Perfil |
| **2** | Ana Souza | `ana@eventhub.com` | `123456` | `PARTICIPANT` | Universidade Federal | Explorar Eventos, Minhas Inscrições, Pagamentos, Meus Certificados, Meu Perfil |
| **3** | Bruno Lima | `bruno@eventhub.com` | `123456` | `PARTICIPANT` | Instituto de Tecnologia | Explorar Eventos, Minhas Inscrições, Pagamentos, Meus Certificados, Meu Perfil |
| **4** | Carla Ferreira | `carla@eventhub.com` | `123456` | `PARTICIPANT` | Tech Corp | Explorar Eventos, Minhas Inscrições, Pagamentos, Meus Certificados, Meu Perfil |

---

## Como Utilizar

1. Acesse `http://localhost:8080` no navegador.
2. Na **Tela de Login**, informe o **E-mail** e a **Senha** (`123456`) de um dos usuários acima.
3. Caso deseje criar um novo usuário do zero, clique no botão **"Criar nova conta"** diretamente na tela de login.
4. Após autenticar, o menu lateral exibirá exclusivamente as funcionalidades compatíveis com o perfil (`ORGANIZER` ou `PARTICIPANT`) do usuário autenticado.
