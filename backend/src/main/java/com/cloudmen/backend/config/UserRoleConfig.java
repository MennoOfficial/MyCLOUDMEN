package com.cloudmen.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for user role settings
 */
@Configuration
public class UserRoleConfig {

    @Value("${user.admin.domain:cloudmen.com}")
    private String systemAdminDomain;

    @Value("${user.admin.email:}")
    private String systemAdminEmail;

    public String getSystemAdminDomain() {
        return systemAdminDomain;
    }

    public String getSystemAdminEmail() {
        return systemAdminEmail;
    }

    /**
     * Check if a specific admin email is configured
     * 
     * @return true if admin email is configured
     */
    public boolean hasAdminEmail() {
        return systemAdminEmail != null && !systemAdminEmail.isEmpty();
    }
}