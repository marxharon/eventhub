-- =============================================================================
-- EVENTHUB - SCRIPT DE INICIALIZAÇÃO DO BANCO DE DADOS ÚNICO (PostgreSQL 16)
-- Contempla estrutura para RF01 a RF11 e Regras de Negócio RN01 a RN07
-- =============================================================================

-- 1. TABELA DE USUÁRIOS E PERFIS (Proprietário: user-service | RF01, RN07)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('PARTICIPANT', 'ORGANIZER')),
    organization_name VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. TABELA DE EVENTOS (Proprietário: event-service | RF02, RN07)
CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    organization VARCHAR(150) NOT NULL,
    organizer_id BIGINT NOT NULL REFERENCES users(id),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. TABELA DE ATIVIDADES DA PROGRAMAÇÃO (Proprietário: event-service / Lock: registration-service | RF03, RF06, RN01, RN06)
CREATE TABLE IF NOT EXISTS activities (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    speaker VARCHAR(150) NOT NULL,
    location VARCHAR(150) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    occupied_slots INTEGER NOT NULL DEFAULT 0 CHECK (occupied_slots >= 0 AND occupied_slots <= capacity),
    price NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (price >= 0.00),
    waitlist_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. TABELA DE INSCRIÇÕES (Proprietário: registration-service | RF05, RF06, RF09, RN01 a RN04)
CREATE TABLE IF NOT EXISTS registrations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    activity_id BIGINT NOT NULL REFERENCES activities(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('CONFIRMED', 'PENDING_PAYMENT', 'WAITLIST', 'CANCELLED', 'REFUSED', 'EXPIRED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

-- Índice único parcial para impedir inscrição ativa duplicada do mesmo participante na mesma atividade (RF09)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_registration
ON registrations (user_id, activity_id)
WHERE status IN ('CONFIRMED', 'PENDING_PAYMENT', 'WAITLIST');

-- 5. TABELA DE PRESENÇAS / CHECK-IN (Proprietário: event-service | RF09, RN06)
CREATE TABLE IF NOT EXISTS attendances (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL UNIQUE REFERENCES registrations(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    activity_id BIGINT NOT NULL REFERENCES activities(id),
    checked_in_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attendance_user_activity UNIQUE (user_id, activity_id)
);

-- 6. TABELA DE CERTIFICADOS (Proprietário: event-service | RF10, RN06)
CREATE TABLE IF NOT EXISTS certificates (
    id BIGSERIAL PRIMARY KEY,
    validation_code VARCHAR(64) NOT NULL UNIQUE,
    registration_id BIGINT NOT NULL UNIQUE REFERENCES registrations(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    activity_id BIGINT NOT NULL REFERENCES activities(id),
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. TABELA DE PAGAMENTOS (Proprietário: payment-service | RF07, RN02, RN03)
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    registration_id BIGINT NOT NULL REFERENCES registrations(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0.00),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REFUSED', 'EXPIRED')),
    provider_ref VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Durable callback pending flag; also applied at payment-service startup for existing volumes.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS registration_sync_pending BOOLEAN NOT NULL DEFAULT FALSE;

-- 8. TABELA DE NOTIFICAÇÕES (Proprietário: notification-service | RF08, RN05)
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    recipient_email VARCHAR(150) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'FAILED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

-- Bancos criados antes da coluna is_read
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT FALSE;

-- =============================================================================
-- DADOS INICIAIS DE EXEMPLO (SEED DATA - RQ08)
-- Senha padrão para todos os usuários de teste: "123456"
-- Hash BCrypt correspondente a "123456": $2a$10$NXhC2OSO8Q7odT3IZkCb5.nyOjN3JB1mhykOUQirAXeEgArY.KNq6
-- =============================================================================

INSERT INTO users (name, email, password_hash, role, organization_name) VALUES
('Prof. Carlos Mendes (Organizador)', 'organizador@eventhub.com', '$2a$10$NXhC2OSO8Q7odT3IZkCb5.nyOjN3JB1mhykOUQirAXeEgArY.KNq6', 'ORGANIZER', 'SBC - Sociedade Brasileira de Computação'),
('Ana Souza (Participante)', 'ana@eventhub.com', '$2a$10$NXhC2OSO8Q7odT3IZkCb5.nyOjN3JB1mhykOUQirAXeEgArY.KNq6', 'PARTICIPANT', 'Universidade Federal'),
('Bruno Lima (Participante)', 'bruno@eventhub.com', '$2a$10$NXhC2OSO8Q7odT3IZkCb5.nyOjN3JB1mhykOUQirAXeEgArY.KNq6', 'PARTICIPANT', 'Instituto de Tecnologia'),
('Carla Ferreira (Participante)', 'carla@eventhub.com', '$2a$10$NXhC2OSO8Q7odT3IZkCb5.nyOjN3JB1mhykOUQirAXeEgArY.KNq6', 'PARTICIPANT', 'Tech Corp')
ON CONFLICT (email) DO NOTHING;

INSERT INTO events (title, description, organization, organizer_id, start_date, end_date, status) VALUES
(
    'Congresso Brasileiro de Arquitetura de Software e Microsserviços 2026',
    'Evento acadêmico e profissional dedicado a padrões de sistemas distribuídos, resiliência, escalabilidade e engenharia de software moderna.',
    'SBC - Sociedade Brasileira de Computação',
    1,
    '2026-11-20 08:00:00',
    '2026-11-22 18:00:00',
    'PUBLISHED'
),
(
    'Simpósio de Inteligência Artificial Aplicada 2026',
    'Seminário avançado sobre LLMs, MLOps e arquiteturas orientadas a dados.',
    'SBC - Sociedade Brasileira de Computação',
    1,
    '2026-12-05 09:00:00',
    '2026-12-06 18:00:00',
    'DRAFT'
);

INSERT INTO activities (event_id, title, description, speaker, location, start_time, end_time, capacity, occupied_slots, price, waitlist_enabled, completed) VALUES
(
    1,
    'Keynote de Abertura: Do Monólito aos Microsserviços Resilientes',
    'Palestra inaugural gratuita aberta aos congressistas sobre decomposição de domínios e trade-offs arquiteturais.',
    'Dra. Helena Costa',
    'Auditório Principal A',
    '2026-11-20 09:00:00',
    '2026-11-20 11:00:00',
    5,
    0,
    0.00,
    TRUE,
    FALSE
),
(
    1,
    'Workshop Prático: Controle de Concorrência, Pagamentos e Tolerância a Falhas',
    'Minicurso mão na massa (atividade paga) com vagas extremamente limitadas e lista de espera automática habilitada.',
    'Dr. Marcos Vinícius',
    'Laboratório 102',
    '2026-11-20 14:00:00',
    '2026-11-20 18:00:00',
    2,
    0,
    120.00,
    TRUE,
    FALSE
),
(
    1,
    'Painel: Observabilidade e Engenharia de Caos em Produção',
    'Atividade concluída de demonstração para validação rápida de check-in e emissão de certificados (RN06).',
    'Eng. Roberto Alves',
    'Sala VIP B',
    '2025-11-21 10:00:00',
    '2025-11-21 12:00:00',
    3,
    0,
    0.00,
    FALSE,
    TRUE
);
