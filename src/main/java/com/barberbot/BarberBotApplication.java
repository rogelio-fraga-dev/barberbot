package com.barberbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BarberBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(BarberBotApplication.class, args);
    }
}
