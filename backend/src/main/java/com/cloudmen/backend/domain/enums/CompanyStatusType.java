package com.cloudmen.backend.domain.enums;

/**
 * Defines the different statuses a company can have.
 */
public enum CompanyStatusType {
    ACTIVE, // Company is active and has full access
    DEACTIVATED, // Company is deactivated (no access)
    SUSPENDED // Company is temporarily suspended
}