-- 1. Tabela de Clientes
CREATE TABLE customers (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255),
    paused_until TIMESTAMP, -- Campo novo para o Modo Pausa
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 2. Tabela de Interações (Histórico de Conversa)
CREATE TABLE interactions (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL, -- USER ou BOT
    content TEXT,
    timestamp TIMESTAMP,
    message_id VARCHAR(255),
    CONSTRAINT fk_customer_interaction FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- 3. Tabela de Tarefas Agendadas (Motor de Lembretes)
CREATE TABLE scheduled_tasks (
    id UUID PRIMARY KEY,
    customer_phone VARCHAR(20) NOT NULL,
    execution_time TIMESTAMP NOT NULL,
    task_type VARCHAR(50) NOT NULL, -- REMINDER, REVIEW_REQUEST
    message_content TEXT,
    status VARCHAR(20) NOT NULL, -- PENDING, COMPLETED, FAILED
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 4. Índices para Performance (Deixa o robô rápido)
CREATE INDEX idx_customer_phone ON customers(phone_number);
CREATE INDEX idx_task_status_time ON scheduled_tasks(status, execution_time);
CREATE INDEX idx_interaction_date ON interactions(timestamp);