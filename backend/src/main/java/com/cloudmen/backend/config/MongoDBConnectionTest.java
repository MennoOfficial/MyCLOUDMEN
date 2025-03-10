package com.cloudmen.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoDBConnectionTest {

    @Bean
    public CommandLineRunner testMongoConnection(MongoTemplate mongoTemplate) {
        return args -> {
            System.out.println("MongoDB connection test:");
            System.out.println("Connected to database: " + mongoTemplate.getDb().getName());
            System.out.println("Collections: " + mongoTemplate.getCollectionNames());
        };
    }
}