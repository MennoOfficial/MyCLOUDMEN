package com.cloudmen.backend.domain.enums;

/**
 * Defines the different user roles within the system.
 */
public enum RoleType {
    SYSTEM_ADMIN, // Administrator with full system access
    COMPANY_ADMIN, // Administrator of a specific company
    COMPANY_USER // Standard user within a company
}
