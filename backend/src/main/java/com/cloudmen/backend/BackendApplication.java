package com.cloudmen.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class BackendApplication {

    @Bean
    public Dotenv dotenv() {
        return Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    public static void main(String[] args) {
        // Load .env file before application starts
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        // Set environment variables from .env file
        if (dotenv != null) {
            // MongoDB
            System.setProperty("MONGODB_URI", dotenv.get("MONGODB_URI", ""));

            // Auth0
            System.setProperty("AUTH0_AUDIENCE", dotenv.get("AUTH0_AUDIENCE", ""));
            System.setProperty("AUTH0_ISSUER_URI", dotenv.get("AUTH0_ISSUER_URI", ""));

            // Teamleader
            System.setProperty("TEAMLEADER_CLIENT_ID", dotenv.get("TEAMLEADER_CLIENT_ID", ""));
            System.setProperty("TEAMLEADER_CLIENT_SECRET", dotenv.get("TEAMLEADER_CLIENT_SECRET", ""));
            System.setProperty("TEAMLEADER_REDIRECT_URI", dotenv.get("TEAMLEADER_REDIRECT_URI", ""));

            // Admin
            System.setProperty("ADMIN_DOMAIN", dotenv.get("ADMIN_DOMAIN", ""));
            System.setProperty("ADMIN_EMAIL", dotenv.get("ADMIN_EMAIL", ""));

            // Google Workspace API
            System.setProperty("GOOGLE_WORKSPACE_API_URL",
                    dotenv.get("GOOGLE_WORKSPACE_API_URL", "https://mycloudmen.mennoplochaet.be/mock-api"));
        }

        SpringApplication.run(BackendApplication.class, args);
    }
}