# 💈 BarberBot - LH Barbearia Assist

O **BarberBot** é um assistente virtual inteligente e um sistema de gestão de relacionamento com o cliente (CRM) construído exclusivamente para o WhatsApp da LH Barbearia.

Movido por Inteligência Artificial, ele atua em duas frentes: como uma **Recepcionista Virtual** 24/7 para os clientes e como um **Painel de Controle de Bolso** para o administrador (Luiz).

## 🚀 Principais Funcionalidades

### 👥 Para os Clientes (Recepcionista IA)

- **Atendimento Natural:** Conversa humanizada usando GPT-4o, com foco em conversão e agendamentos.
- **Leitura de Áudio:** Entende mensagens de voz enviadas pelos clientes usando OpenAI Whisper.
- **Filtro Anti-Spam:** Ignora figurinhas silenciosamente para evitar respostas desnecessárias.
- **Transbordo Humano:** Auto-pausa a inteligência artificial assim que o cliente pede para "falar com o Luiz".

### 👑 Para o Administrador (Painel no WhatsApp)

O administrador controla todo o sistema mandando mensagens ou áudios para o próprio bot:

- **Leitura de Agenda via Imagem:** O Admin envia um print da agenda (CashBarber), a IA lê os nomes e horários usando Visão Computacional e cruza com o Banco de Dados.
- **Lembretes Automáticos:** O Bot avisa o cliente automaticamente **exatamente 1 hora antes** do seu corte.
- **Disparos em Massa (Broadcast):** Opção de enviar avisos gerais ou mensagens de prospecção para a base.
- **Gestão de Pausas:** Religa ou pausa o bot para clientes específicos diretamente pelo WhatsApp.
- **Importação de Base (CSV):** Atualização instantânea do banco de dados ao enviar um arquivo `.csv` pelo chat.

## 🛠️ Tecnologias Utilizadas

- **Java 23** + **Spring Boot 3.2.0**
- **PostgreSQL** (Banco de dados relacional)
- **Evolution API** (Integração não-oficial e robusta com WhatsApp)
- **LangChain4j** (Orquestração da Inteligência Artificial)
- **OpenAI (GPT-4o & Whisper-1)** (Cérebro do chatbot, Visão e Transcrição)

## ⚙️ Como Executar o Projeto

### Pré-requisitos

1. PostgreSQL rodando localmente ou em nuvem.
2. Evolution API rodando e com a instância do WhatsApp conectada.
3. Chaves de API da OpenAI e da Evolution configuradas.

### Instalação e Execução

1. Clone este repositório.
2. Crie um arquivo `.env` na raiz do projeto com as seguintes variáveis:
   ```ini
   OPENAI_API_KEY=sk-...
   EVOLUTION_API_KEY=sua_apikey_aqui
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=sua_senha
   POSTGRES_DB=barberbot
   ADMIN_PHONE=5534999999999
   Execute o script de inicialização pelo PowerShell:
   ```

PowerShell
.\run.ps1
O Spring Boot iniciará na porta 8081 e conectará automaticamente aos Webhooks da Evolution API.

📱 Menu de Comandos (Admin)
Envie qualquer comando de texto ou áudio para o Bot sendo o número Administrador:

1 ou Resumo - Exibe o status do bot e clientes cadastrados.

2 ou Avisos - Inicia disparo para a base atual.

3 ou Prospecção - Inicia disparo de marketing.

4 ou Pausar - Silencia o bot para um DDD + Número.

5 ou Retomar - Religa o bot via menu interativo de clientes pausados.

6 ou Agenda - Mostra a agenda processada do dia.

7 ou Importar - Salva um cliente (Nome, Telefone) manualmente.

Desenvolvido com ☕ e foco total na automatização de barbearias de alto nível.
