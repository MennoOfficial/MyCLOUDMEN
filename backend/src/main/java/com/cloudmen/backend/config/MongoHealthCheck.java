package com.cloudmen.backend.config;

import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;

@Component
public class MongoHealthCheck implements HealthIndicator {

    private final MongoTemplate mongoTemplate;

    public MongoHealthCheck(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Health health() {
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            return Health.up().withDetail("service", "MongoDB").build();
        } catch (Exception e) {
            return Health.down().withDetail("service", "MongoDB")
                    .withDetail("error", e.getMessage()).build();
        }
    }
}