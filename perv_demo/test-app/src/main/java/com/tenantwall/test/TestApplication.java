package com.tenantwall.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
        System.out.println("[START] Test Application is Running on http://localhost:8080");
        System.out.println("[DATA] جرب: http://localhost:8080/users");
    }
}