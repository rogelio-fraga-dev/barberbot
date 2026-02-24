package com.barberbot.api.service;

import com.barberbot.api.model.Customer;
import com.barberbot.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CustomerService {

    private final CustomerRepository customerRepository;
    
    // Memória viva: Guarda o telefone do cliente e o horário que a pausa acaba
    private final Map<String, LocalDateTime> pausedCustomers = new ConcurrentHashMap<>();

    public Customer findOrCreateCustomer(String phoneNumber, String name) {
        return customerRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .phoneNumber(phoneNumber)
                        .name(name != null ? name : "Cliente")
                        .build()));
    }
    
    public Customer findByName(String name) {
        return customerRepository.findByNameIgnoreCase(name.trim()).orElse(null);
    }

    public void pauseCustomer(String phoneNumber, int minutes) {
        pausedCustomers.put(phoneNumber, LocalDateTime.now().plusMinutes(minutes));
        log.info("Pausando cliente {} por {} minutos", phoneNumber, minutes);
    }

    public void resumeCustomer(String phoneNumber) {
        pausedCustomers.remove(phoneNumber);
        log.info("Retomando cliente {}", phoneNumber);
    }

    public boolean isCustomerPaused(String phoneNumber) {
        LocalDateTime unpauseTime = pausedCustomers.get(phoneNumber);
        if (unpauseTime != null) {
            if (LocalDateTime.now().isAfter(unpauseTime)) {
                pausedCustomers.remove(phoneNumber); // O tempo expirou, tira da pausa
                return false;
            }
            return true; // Ainda está pausado
        }
        return false;
    }
    
    // NOVO: Retorna a lista de números pausados para o Luiz escolher
    public List<String> getPausedPhones() {
        // Primeiro limpa os que já venceram o tempo (1 hora)
        pausedCustomers.entrySet().removeIf(e -> LocalDateTime.now().isAfter(e.getValue()));
        return new ArrayList<>(pausedCustomers.keySet());
    }

    @org.springframework.transaction.annotation.Transactional
    public int importCustomersFromCsvBase64(String base64Content) {
        try {
            String pureBase64 = base64Content.contains(",") ? base64Content.split(",")[1] : base64Content;
            byte[] decodedBytes = Base64.getDecoder().decode(pureBase64);
            String csvText = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
            
            String[] lines = csvText.split("\\r?\\n");
            int count = 0;
            
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.trim().isEmpty()) continue;
                
                String[] columns = line.split("\",\"");
                if (columns.length >= 5) {
                    String rawName = columns[1].replace("\"", "").trim();
                    String rawPhone = columns[4].replace("\"", "").trim();
                    
                    if (!rawPhone.isEmpty() && !rawPhone.equalsIgnoreCase("N/A")) {
                        String cleanPhone = rawPhone.replaceAll("[^0-9]", "");
                        if (cleanPhone.length() == 10 || cleanPhone.length() == 11) cleanPhone = "55" + cleanPhone;
                        
                        Customer customer = customerRepository.findByPhoneNumber(cleanPhone).orElse(new Customer());
                        customer.setPhoneNumber(cleanPhone);
                        customer.setName(rawName); 
                        customerRepository.save(customer);
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            log.error("Erro importando CSV: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}