package com.cloudmen.backend.api.dtos.googleworkspace;

import java.util.List;

/**
 * Response containing summary of all Google Workspace subscriptions
 */
public class GoogleWorkspaceSubscriptionListResponseDTO {
    private List<GoogleWorkspaceSubscriptionDTO> subscriptions;
    private int totalSubscriptions;

    public GoogleWorkspaceSubscriptionListResponseDTO() {
    }

    public GoogleWorkspaceSubscriptionListResponseDTO(List<GoogleWorkspaceSubscriptionDTO> subscriptions) {
        this.subscriptions = subscriptions;
        this.totalSubscriptions = subscriptions != null ? subscriptions.size() : 0;
    }

    public List<GoogleWorkspaceSubscriptionDTO> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<GoogleWorkspaceSubscriptionDTO> subscriptions) {
        this.subscriptions = subscriptions;
        this.totalSubscriptions = subscriptions != null ? subscriptions.size() : 0;
    }

    public int getTotalSubscriptions() {
        return totalSubscriptions;
    }

    public void setTotalSubscriptions(int totalSubscriptions) {
        this.totalSubscriptions = totalSubscriptions;
    }
}