package com.barberbot.api.service;

import com.barberbot.api.model.Customer;
import com.barberbot.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {
    
    private final CustomerRepository customerRepository;
    
    /**
     * Busca ou cria um cliente pelo número de telefone
     */
    @Transactional
    public Customer findOrCreateCustomer(String phoneNumber, String name) {
        Optional<Customer> existing = customerRepository.findByPhoneNumber(phoneNumber);
        
        if (existing.isPresent()) {
            Customer customer = existing.get();
            // Atualiza o nome se foi fornecido e diferente
            if (name != null && !name.isEmpty() && !name.equals(customer.getName())) {
                customer.setName(name);
                customer = customerRepository.save(customer);
                log.info("Nome do cliente {} atualizado para: {}", phoneNumber, name);
            }
            return customer;
        }
        
        Customer newCustomer = Customer.builder()
                .phoneNumber(phoneNumber)
                .name(name)
                .build();
        
        Customer saved = customerRepository.save(newCustomer);
        log.info("Novo cliente criado: {} - {}", phoneNumber, name);
        return saved;
    }
    
    /**
     * Busca cliente pelo número de telefone
     */
    public Optional<Customer> findByPhoneNumber(String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber);
    }
    
    /**
     * Verifica se um número pertence ao admin
     */
    public boolean isAdmin(String phoneNumber) {
        // Lógica será implementada no OrchestratorService usando BarberBotProperties
        return false;
    }
}
