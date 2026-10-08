package com.datashield.test;

import com.datashield.test.entity.User;
import com.datashield.test.entity.Order;
import com.datashield.test.entity.Customer;
import com.datashield.test.repository.UserRepository;
import com.datashield.test.repository.OrderRepository;
import com.datashield.test.repository.CustomerRepository;
import com.github.javafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final CustomerRepository customerRepo;

    public DataInitializer(UserRepository userRepo, OrderRepository orderRepo, CustomerRepository customerRepo) {
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.customerRepo = customerRepo;
    }

    @Override
    public void run(String... args) {
        // نشوف لو فيه بيانات قبل ما نضيف عشان ما نكررش
        if (userRepo.count() > 100) {
            System.out.println("✅ البيانات موجودة مسبقاً، تخطي الإضافة.");
            return;
        }

        Faker faker = new Faker(new Locale("ar"));
        List<User> users = new ArrayList<>();
        List<Customer> customers = new ArrayList<>();
        List<Order> orders = new ArrayList<>();

        // نعمل ١٠ Tenants مختلفين
        String[] tenants = {"TENANT_A", "TENANT_B", "TENANT_C", "TENANT_D", "TENANT_E"};

        // نضيف ١٠٠٠ مستخدم و ٥٠٠ عميل و ٢٠٠٠ طلب
        for (int i = 0; i < 1000; i++) {
            String tenant = tenants[i % tenants.length];
            User user = new User(faker.name().fullName(), tenant);
            users.add(user);
        }
        userRepo.saveAll(users);

        for (int i = 0; i < 500; i++) {
            String tenant = tenants[i % tenants.length];
            Customer customer = new Customer(faker.name().fullName(), tenant, faker.phoneNumber().cellPhone());
            customers.add(customer);
        }
        customerRepo.saveAll(customers);

        // ٢٠٠٠ طلب (لكن هنربطهم بمستخدمين موجودين)
        for (int i = 0; i < 2000; i++) {
            String tenant = tenants[i % tenants.length];
            User randomUser = users.get(faker.random().nextInt(users.size()));
            Order order = new Order(tenant, randomUser.getId(), faker.number().randomDouble(2, 10, 5000));
            orders.add(order);
        }
        orderRepo.saveAll(orders);

        System.out.println("✅ تم إضافة " + users.size() + " مستخدم، " + customers.size() + " عميل، " + orders.size() + " طلب.");
         com.datashield.agent.SqlModifier.clearCache();
   
    }
}