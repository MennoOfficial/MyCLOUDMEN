package com.cloudmen.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TeamleaderConfig {
    // Development environment custom field IDs
    public static final String DEV_MYCLOUDMEN_ACCESS_FIELD_ID = "9faf2006-c6ed-07ec-b25d-131116783b7b";

    // Production environment custom field IDs (to be filled in later)
    public static final String PROD_MYCLOUDMEN_ACCESS_FIELD_ID = ""; // TODO: Add production field ID

    @Value("${teamleader.environment:development}")
    private String environment;

    public String getMyCloudmenAccessFieldId() {
        return "production".equalsIgnoreCase(environment)
                ? PROD_MYCLOUDMEN_ACCESS_FIELD_ID
                : DEV_MYCLOUDMEN_ACCESS_FIELD_ID;
    }
}