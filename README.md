# BarberBot Assist

Sistema de automação de atendimento via WhatsApp para barbearia, com funcionalidades de recepcionista virtual e assistente administrativo.

## 📖 Sobre o Projeto

O BarberBot Assist é um robô inteligente desenvolvido para automatizar o atendimento da barbearia via WhatsApp. O sistema atua em duas frentes:

### Recepcionista Virtual
Atende clientes automaticamente, oferecendo:
- Menu interativo com opções de serviços
- Informações sobre preços e localização
- Link para agendamento
- Respostas inteligentes via IA
- Histórico completo de conversas

### Assistente Administrativo
Funciona como um funcionário virtual para gestão:
- Processamento automático de agenda via imagem
- Agendamento de pesquisas de satisfação
- Gerenciamento de contatos
- Disparo de mensagens em massa (com controle anti-spam)

## 🚀 Como Iniciar

### Pré-requisitos
- Java 17 ou superior
- Maven 3.6+
- Docker e Docker Compose
- API Key da OpenAI

### Passo a Passo

1. **Configurar API Key da OpenAI**
   ```bash
   # Windows PowerShell
   $env:OPENAI_API_KEY="sua-api-key-aqui"
   
   # Linux/Mac
   export OPENAI_API_KEY="sua-api-key-aqui"
   ```

2. **Iniciar containers Docker**
   ```bash
   docker-compose up -d
   ```

3. **Aguardar PostgreSQL ficar pronto**
   ```bash
   docker-compose ps
   ```

4. **Compilar e executar o projeto**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

5. **Configurar Evolution API**
   - Acesse http://localhost:8080
   - Crie uma instância
   - Configure o webhook para: `http://host.docker.internal:8081/api/webhook`
   - Escaneie o QR Code com WhatsApp

## 📁 Estrutura do Projeto

```
barberbot/
├── src/main/java/com/barberbot/
│   ├── BarberBotApplication.java
│   └── api/
│       ├── controller/
│       │   └── WebhookController.java
│       ├── service/
│       │   ├── OrchestratorService.java
│       │   ├── CustomerService.java
│       │   ├── AgendaService.java
│       │   ├── OpenAIService.java
│       │   └── WhatsAppService.java
│       ├── client/
│       │   └── EvolutionClient.java
│       ├── model/
│       │   ├── Customer.java
│       │   ├── Interaction.java
│       │   └── ScheduledTask.java
│       ├── dto/
│       │   ├── EvolutionWebhookDTO.java
│       │   ├── MessageDTO.java
│       │   └── AgendaDTO.java
│       ├── repository/
│       │   ├── CustomerRepository.java
│       │   ├── InteractionRepository.java
│       │   └── ScheduledTaskRepository.java
│       ├── scheduler/
│       │   └── TaskScheduler.java
│       └── config/
│           ├── BarberBotProperties.java
│           ├── OpenAIConfig.java
│           └── WebClientConfig.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__create_tables.sql
├── docker-compose.yml
├── pom.xml
├── README.md
└── CHECKLIST.md
```

## 🔧 Configurações

### Variáveis de Ambiente Necessárias

- `OPENAI_API_KEY` - **OBRIGATÓRIA** - API Key da OpenAI
- `EVOLUTION_API_KEY` - Opcional - Se a Evolution API exigir autenticação

### Configurações no application.yml

- `barberbot.admin.phone` - Número do administrador (Luiz)
- `barberbot.schedule.delay-minutes` - Delay para envio de avaliação (padrão: 60)
- `barberbot.schedule.batch-size` - Tamanho do lote de mensagens (padrão: 1)
- `barberbot.schedule.delay-between-messages` - Delay entre mensagens em ms (padrão: 60000)

## 📝 Funcionalidades

### Recepcionista Virtual
- Atendimento automático de clientes
- Menu interativo
- Respostas inteligentes via IA
- Histórico de conversas

### Assistente Administrativo
- Processamento de agenda via imagem
- Agendamento automático de avaliações
- Disparo de mensagens (em desenvolvimento)
- Gerenciamento de contatos

## 🏗️ Arquitetura

O sistema segue uma arquitetura de microsserviços modular:

- **Backend (Spring Boot)**: Regra de negócio e processamento
- **Evolution API (Docker)**: Gateway para WhatsApp via webhooks
- **OpenAI (API Externa)**: Processamento de linguagem natural e visão computacional
- **PostgreSQL**: Armazenamento de dados (clientes, interações, tarefas agendadas)

### Fluxo de Comunicação

```
[WhatsApp Cliente] 
       ⬇️ (Mensagem)
[Evolution API] 
       ⬇️ (Webhook HTTP POST)
[WebhookController] 
       ⬇️
[OrchestratorService] ➡️ [PostgreSQL] (Consulta Contexto)
       ⬇️
[OpenAIService] ➡️ [OpenAI API]
       ⬇️ (Resposta Inteligente)
[WhatsAppService] ➡️ [Evolution API]
       ⬇️
[WhatsApp Cliente]
```

## 🐛 Troubleshooting

### PostgreSQL não inicia
- Verifique se a porta 5432 não está em uso
- Execute: `docker-compose down -v` e reinicie

### Evolution API não conecta
- Verifique se o container está rodando: `docker ps`
- Confirme que o webhook está configurado corretamente
- Verifique os logs: `docker logs barberbot-evolution-api`

### Erro de API Key
- Confirme que a variável de ambiente está definida
- Verifique se a API Key é válida
- Teste a API Key diretamente no console da OpenAI

### Aplicação não inicia
- Verifique se o PostgreSQL está rodando
- Confirme que a porta 8081 não está em uso
- Verifique os logs: `mvn spring-boot:run` ou verifique os logs do IDE

## 📚 Tecnologias Utilizadas

- **Java 17** - Linguagem de programação
- **Spring Boot 3.2** - Framework
- **PostgreSQL 15** - Banco de dados
- **Flyway** - Versionamento de banco
- **LangChain4j** - Integração com LLMs
- **Evolution API** - Gateway WhatsApp
- **Docker** - Containerização

## 📋 Checklist e Pendências

Para ver o checklist completo de implementação e pendências, consulte o arquivo [CHECKLIST.md](./CHECKLIST.md).

## 👨‍💻 Desenvolvido por

Rogélio Claro Fraga

## 📄 Licença

Projeto privado para uso interno da Barbearia do Luiz.
