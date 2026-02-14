package com.barberbot.api.service;

import com.barberbot.api.model.Customer;
import com.barberbot.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
    
    private final CustomerRepository customerRepository;
    
    @Transactional
    public Customer findOrCreateCustomer(String phoneNumber, String name) {
        Optional<Customer> existing = customerRepository.findByPhoneNumber(phoneNumber);
        
        if (existing.isPresent()) {
            Customer customer = existing.get();
            if (name != null && !name.isEmpty() && !name.equals(customer.getName())) {
                customer.setName(name);
                customer = customerRepository.save(customer);
            }
            return customer;
        }
        
        Customer newCustomer = Customer.builder()
                .phoneNumber(phoneNumber)
                .name(name)
                .build();
        
        return customerRepository.save(newCustomer);
    }
    
    public boolean isCustomerPaused(String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber)
                .map(Customer::isPaused)
                .orElse(false);
    }

    @Transactional
    public void pauseCustomer(String phoneNumber, int minutes) {
        Optional<Customer> customerOpt = customerRepository.findByPhoneNumber(phoneNumber);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setPausedUntil(LocalDateTime.now().plusMinutes(minutes));
            customerRepository.save(customer);
            log.info("Cliente {} pausado por {} minutos.", phoneNumber, minutes);
        }
    }

    @Transactional
    public void resumeCustomer(String phoneNumber) {
        Optional<Customer> customerOpt = customerRepository.findByPhoneNumber(phoneNumber);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setPausedUntil(null);
            customerRepository.save(customer);
            log.info("Cliente {} retomado (bot ativo).", phoneNumber);
        }
    }
    
    /**
     * Importa uma lista de contatos no formato "numero,nome;numero,nome"
     */
    @Transactional
    public int importContactsFromText(String textList) {
        int count = 0;
        String[] entries = textList.split(";"); // Separa por ponto e vírgula
        
        for (String entry : entries) {
            try {
                String[] parts = entry.trim().split(","); // Separa numero de nome
                if (parts.length >= 1) {
                    String phone = parts[0].trim().replaceAll("[^0-9]", "");
                    String name = parts.length > 1 ? parts[1].trim() : "Importado";
                    
                    if (!phone.isEmpty()) {
                        findOrCreateCustomer(phone, name);
                        count++;
                    }
                }
            } catch (Exception e) {
                log.error("Erro ao importar item: {}", entry);
            }
        }
        return count;
    }
}