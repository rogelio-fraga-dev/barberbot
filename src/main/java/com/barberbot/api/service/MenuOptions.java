package com.barberbot.api.service;

import com.barberbot.api.client.EvolutionClient;
import com.barberbot.api.config.BarberBotProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MenuOptions {

    public static final String ROW_ID_ENDERECO = "menu_endereco";
    public static final String ROW_ID_SERVICOS = "menu_servicos";
    public static final String ROW_ID_PRODUTOS = "menu_produtos";
    public static final String ROW_ID_AGENDAR = "menu_agendar";
    public static final String ROW_ID_ATENDENTE = "menu_atendente";
    public static final String ROW_ID_INSTAGRAM = "menu_instagram";

    private static final String[] ALL_ROW_IDS = {
            ROW_ID_ENDERECO, ROW_ID_SERVICOS, ROW_ID_PRODUTOS,
            ROW_ID_AGENDAR, ROW_ID_ATENDENTE, ROW_ID_INSTAGRAM
    };

    private static final String[] ROW_IDS_BY_NUMBER = {
            ROW_ID_ENDERECO, ROW_ID_SERVICOS, ROW_ID_PRODUTOS,
            ROW_ID_AGENDAR, ROW_ID_ATENDENTE, ROW_ID_INSTAGRAM
    };

    public static String resolveMenuOptionId(String messageText) {
        if (messageText == null) return null;
        String t = messageText.trim();
        for (String id : ALL_ROW_IDS) {
            if (id.equals(t)) return id;
        }
        if (t.length() == 1 && t.charAt(0) >= '1' && t.charAt(0) <= '6') {
            return ROW_IDS_BY_NUMBER[t.charAt(0) - '1'];
        }
        return null;
    }

    public static boolean isAskingForMenu(String messageText) {
        if (messageText == null) return false;
        String t = messageText.trim().toLowerCase();
        return t.equals("menu") || t.equals("opções") || t.equals("opcoes")
                || t.startsWith("ver op") || t.contains("tabela") || t.contains("preço");
    }

    /**
     * Monta a LISTA INTERATIVA (Botões nativos do WhatsApp)
     */
    public static List<Map<String, Object>> buildListSections() {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(EvolutionClient.listRow(ROW_ID_ENDERECO, "📍 Endereço", "Localização e Mapa"));
        rows.add(EvolutionClient.listRow(ROW_ID_SERVICOS, "💰 Serviços e Planos", "Tabela de preços VIP"));
        rows.add(EvolutionClient.listRow(ROW_ID_AGENDAR, "📅 Agendar Horário", "Link do CashBarber"));
        rows.add(EvolutionClient.listRow(ROW_ID_PRODUTOS, "💈 Produtos", "O que vendemos"));
        rows.add(EvolutionClient.listRow(ROW_ID_INSTAGRAM, "📸 Instagram", "Nossas redes"));
        rows.add(EvolutionClient.listRow(ROW_ID_ATENDENTE, "🗣️ Falar com Luiz", "Atendimento humano"));

        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(EvolutionClient.listSection("Escolha uma opção", rows));
        return sections;
    }

    public static String getResponseForOption(String rowId, BarberBotProperties properties) {
        BarberBotProperties.Menu menu = properties.getMenu();

        switch (rowId) {
            case ROW_ID_ENDERECO:
                // Link formatado para ficar mais curto visualmente (embora a URL seja a mesma)
                return menu.getAddressText() + "\n\n🗺️ *Abrir no Maps:* " + menu.getAddressMapsUrl();
            
            case ROW_ID_SERVICOS:
                return menu.getServicesText() + "\n👉 " + menu.getScheduleUrl();
            
            case ROW_ID_PRODUTOS:
                return "💈 *Produtos LH Barbearia*\n\nTemos pomadas, óleos e minoxidil disponíveis na bancada.\nPergunte ao seu barbeiro no próximo corte!";
            
            case ROW_ID_AGENDAR:
                return "✂️ *Agende seu horário agora:*\n" + menu.getScheduleUrl() + "\n\nEscolha o barbeiro e o serviço de sua preferência!";
            
            case ROW_ID_ATENDENTE:
                return "🗣️ *Chamando o Luiz...*\n\nJá notifiquei ele aqui. Assim que ele desocupar, ele te responde!\n\nEnquanto isso, se quiser agendar, o link está no menu.";
            
            case ROW_ID_INSTAGRAM:
                return "📸 *Siga a LH Barbearia!*\n\nFique por dentro dos cortes e novidades:\n" + menu.getInstagramUrl();
            
            default:
                return "Opção não reconhecida. Digite *Menu* para ver as opções.";
        }
    }

    public static String getMenuAsText() {
        return """
                💈 *Menu LH Barbearia*
                
                1️⃣ Endereço e Localização
                2️⃣ Serviços e Preços (Planos VIP)
                3️⃣ Produtos
                4️⃣ Agendar Horário 📅
                5️⃣ Falar com o Luiz (Atendente)
                6️⃣ Instagram
                
                _Digite o número da opção desejada ou clique no botão abaixo._""";
    }
}