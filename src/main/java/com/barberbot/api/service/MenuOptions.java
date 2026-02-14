package com.barberbot.api.service;

import com.barberbot.api.client.EvolutionClient;
import com.barberbot.api.config.BarberBotProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Opções do menu interativo (lista clicável no WhatsApp).
 * Quando o usuário toca em uma opção, a Evolution API envia o rowId como texto.
 */
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

    /** Mapeia "1" a "6" para os rowIds (quando o menu é enviado em texto). */
    private static final String[] ROW_IDS_BY_NUMBER = {
            ROW_ID_ENDERECO, ROW_ID_SERVICOS, ROW_ID_PRODUTOS,
            ROW_ID_AGENDAR, ROW_ID_ATENDENTE, ROW_ID_INSTAGRAM
    };

    /**
     * Converte mensagem em id de opção do menu (rowId ou "1"-"6").
     */
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

    public static boolean isMenuOptionId(String messageText) {
        return resolveMenuOptionId(messageText) != null;
    }

    /**
     * Monta as seções da lista para enviar ao WhatsApp (uma seção "Menu" com todas as opções).
     */
    public static List<Map<String, Object>> buildListSections() {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(EvolutionClient.listRow(ROW_ID_ENDERECO, "📍 Endereço", "Texto + Google Maps"));
        rows.add(EvolutionClient.listRow(ROW_ID_SERVICOS, "💰 Serviços", "Tabela de preços"));
        rows.add(EvolutionClient.listRow(ROW_ID_PRODUTOS, "💈 Produtos", "Fotos e valores"));
        rows.add(EvolutionClient.listRow(ROW_ID_AGENDAR, "📅 Agendar", "Link para agendamento"));
        rows.add(EvolutionClient.listRow(ROW_ID_ATENDENTE, "🗣️ Falar com Atendente", "Chama o Luiz"));
        rows.add(EvolutionClient.listRow(ROW_ID_INSTAGRAM, "📸 Instagram", "Nos siga nas redes"));
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(EvolutionClient.listSection("Escolha uma opção", rows));
        return sections;
    }

    /**
     * Retorna a resposta pré-definida para a opção do menu selecionada.
     * Usa BarberBotProperties.Menu quando configurado; senão textos padrão.
     */
    public static String getResponseForOption(String rowId, BarberBotProperties properties) {
        BarberBotProperties.Menu menu = properties != null ? properties.getMenu() : null;
        if (menu == null) menu = new BarberBotProperties.Menu();

        switch (rowId) {
            case ROW_ID_ENDERECO:
                String addr = menu.getAddressText();
                String maps = menu.getAddressMapsUrl();
                if (addr != null && !addr.isEmpty()) {
                    return maps != null && !maps.isEmpty() ? addr + "\n\n" + maps : addr;
                }
                return "📍 Nosso endereço e link do Google Maps em breve. Peça para falar com um atendente para mais informações.";
            case ROW_ID_SERVICOS:
                String svc = menu.getServicesText();
                if (svc != null && !svc.isEmpty()) return svc;
                return "💰 Serviços e tabela de preços em atualização. Quer agendar ou falar com um atendente?";
            case ROW_ID_PRODUTOS:
                return "💈 Produtos (fotos e valores) em breve. Enquanto isso, fale com um atendente.";
            case ROW_ID_AGENDAR:
                String link = menu.getScheduleUrl();
                if (link != null && !link.isEmpty()) {
                    return "📅 Agende seu horário pelo link:\n" + link;
                }
                return "📅 Link de agendamento em breve. Enquanto isso, peça para falar com um atendente.";
            case ROW_ID_ATENDENTE:
                return "🗣️ Um atendente (Luiz) será avisado. Em instantes alguém irá te atender!";
            case ROW_ID_INSTAGRAM:
                String ig = menu.getInstagramUrl();
                if (ig != null && !ig.isEmpty()) {
                    return "📸 Nos siga no Instagram:\n" + ig;
                }
                return "📸 Nos siga no Instagram! O link será configurado em breve.";
            default:
                return "Opção não reconhecida. Digite *menu* para ver as opções.";
        }
    }

    /**
     * Texto do menu para fallback quando a lista interativa não for suportada (ex.: Evolution API retorna 400).
     */
    public static String getMenuAsText() {
        return "📋 *Menu de opções:*\n\n"
                + "📍 Endereço (Texto + Google Maps)\n"
                + "💰 Serviços e Tabela de Preços\n"
                + "💈 Produtos (Fotos e Valores)\n"
                + "📅 Agendar Horário (Link externo)\n"
                + "🗣️ Falar com Atendente (Chama o Luiz)\n"
                + "📸 Instagram (Nos siga nas redes)\n\n"
                + "Digite o número da opção (1 a 6) ou o nome da opção.";
    }

    /** Palavras que indicam que o usuário quer ver o menu (para enviar a lista). */
    public static boolean isAskingForMenu(String messageText) {
        if (messageText == null) return false;
        String t = messageText.trim().toLowerCase();
        if (t.isEmpty()) return false;
        return t.equals("menu") || t.equals("opções") || t.equals("opcoes")
                || t.equals("opção") || t.equals("opcao") || t.equals("ver opções")
                || t.equals("ver opcoes") || t.equals("opções por favor")
                || t.startsWith("quero ver o menu") || t.startsWith("mostrar menu");
    }
}
