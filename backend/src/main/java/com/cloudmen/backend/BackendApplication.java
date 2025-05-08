package com.cloudmen.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        // Load .env file before application starts
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        // Set environment variables from .env file
        if (dotenv != null) {
            String user = dotenv.get("MONGO_USER", "");
            String password = dotenv.get("MONGO_PASSWORD", "").replace("!", "%21");
            String cluster = dotenv.get("MONGO_CLUSTER", "");
            String db = dotenv.get("MONGO_DATABASE", "");

            // Construct URI
            String uri = String.format("mongodb+srv://%s:%s@%s/%s", user, password, cluster, db);
            System.setProperty("spring.data.mongodb.uri", uri);

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
                    dotenv.get("GOOGLE_WORKSPACE_API_URL", "https://mycloudmen.mennoplochaet.be/google-workspace-api"));

            // SignatureSatori API
            System.setProperty("SIGNATURESATORI_API_URL",
                    dotenv.get("SIGNATURESATORI_API_URL", "https://mycloudmen.mennoplochaet.be/signature-satori-api"));
            System.setProperty("SIGNATURESATORI_API_TOKEN", dotenv.get("SIGNATURESATORI_API_TOKEN", ""));

            // Email
            System.setProperty("MAIL_HOST", dotenv.get("MAIL_HOST", "smtp.gmail.com"));
            System.setProperty("MAIL_PORT", dotenv.get("MAIL_PORT", "587"));
            System.setProperty("MAIL_USERNAME", dotenv.get("MAIL_USERNAME", "mycloudmen@gmail.com"));
            System.setProperty("MAIL_PASSWORD", dotenv.get("MAIL_PASSWORD", "your-app-password-here"));
        }

        SpringApplication.run(BackendApplication.class, args);
    }
}