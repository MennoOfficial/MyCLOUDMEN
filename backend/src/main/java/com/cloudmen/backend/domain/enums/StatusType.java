package com.cloudmen.backend.domain.enums;

/**
 * Defines the different statuses a user can have.
 */
public enum StatusType {
    ACTIVATED, // Active user with full access
    PENDING, // User waiting for activation
    DEACTIVATED, // User is deactivated
    REJECTED // User request was explicitly rejected
}
