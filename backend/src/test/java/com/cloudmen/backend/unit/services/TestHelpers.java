package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods for unit tests
 */
public class TestHelpers {

    /**
     * Converts the Google API response to our simplified subscription list DTO
     */
    public static GoogleWorkspaceSubscriptionListResponseDTO convertToSimplifiedSubscriptionList(
            JsonNode jsonNode) {
        GoogleWorkspaceSubscriptionListResponseDTO response = new GoogleWorkspaceSubscriptionListResponseDTO();
        List<GoogleWorkspaceSubscriptionDTO> subscriptions = new ArrayList<>();

        if (jsonNode != null && jsonNode.has("subscriptions") && jsonNode.get("subscriptions").isArray()) {
            for (JsonNode subNode : jsonNode.get("subscriptions")) {
                subscriptions.add(convertToSimplifiedSubscription(subNode));
            }
        }

        response.setSubscriptions(subscriptions);
        return response;
    }

    /**
     * Converts a single subscription from the API response to our simplified DTO
     */
    public static GoogleWorkspaceSubscriptionDTO convertToSimplifiedSubscription(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }

        GoogleWorkspaceSubscriptionDTO dto = new GoogleWorkspaceSubscriptionDTO();

        if (jsonNode.has("skuId")) {
            dto.setSkuId(jsonNode.get("skuId").asText());
        }

        if (jsonNode.has("skuName")) {
            dto.setSkuName(jsonNode.get("skuName").asText());
        }

        if (jsonNode.has("status")) {
            dto.setStatus(jsonNode.get("status").asText());
        }

        // Extract plan type
        if (jsonNode.has("plan") && jsonNode.get("plan").has("planName")) {
            dto.setPlanType(jsonNode.get("plan").get("planName").asText());
        }

        // Extract license counts
        if (jsonNode.has("seats")) {
            JsonNode seats = jsonNode.get("seats");
            if (seats.has("numberOfSeats")) {
                dto.setTotalLicenses(seats.get("numberOfSeats").asInt());
            } else if (seats.has("licensedNumberOfSeats")) {
                dto.setTotalLicenses(seats.get("licensedNumberOfSeats").asInt());
            }
        }

        return dto;
    }

    /**
     * Standardize license type names for comparison.
     * This strips "Google Workspace" prefix and normalizes case.
     */
    public static String normalizeGoogleLicenseType(String licenseType) {
        if (licenseType == null) {
            return "";
        }

        // Remove "Google Workspace" prefix if present
        String normalized = licenseType.replaceAll("(?i)Google\\s+Workspace\\s+", "");

        // For common license types, standardize capitalization
        if (normalized.equalsIgnoreCase("business starter")) {
            return "Business Starter";
        } else if (normalized.equalsIgnoreCase("business standard")) {
            return "Business Standard";
        } else if (normalized.equalsIgnoreCase("business plus")) {
            return "Business Plus";
        } else if (normalized.equalsIgnoreCase("enterprise essentials")) {
            return "Enterprise Essentials";
        } else if (normalized.equalsIgnoreCase("enterprise standard")) {
            return "Enterprise Standard";
        } else if (normalized.equalsIgnoreCase("enterprise plus")) {
            return "Enterprise Plus";
        }

        // Return as-is for custom license types
        return normalized;
    }

    /**
     * Create a sample subscription list response
     */
    public static GoogleWorkspaceSubscriptionListResponseDTO createSampleSubscriptionList() {
        GoogleWorkspaceSubscriptionListResponseDTO response = new GoogleWorkspaceSubscriptionListResponseDTO();
        List<GoogleWorkspaceSubscriptionDTO> subscriptions = new ArrayList<>();

        GoogleWorkspaceSubscriptionDTO sub1 = new GoogleWorkspaceSubscriptionDTO();
        sub1.setSkuId("sku-1");
        sub1.setSkuName("Google Workspace Business Starter");
        sub1.setStatus("ACTIVE");
        sub1.setPlanType("ANNUAL");
        sub1.setTotalLicenses(10);
        subscriptions.add(sub1);

        GoogleWorkspaceSubscriptionDTO sub2 = new GoogleWorkspaceSubscriptionDTO();
        sub2.setSkuId("sku-2");
        sub2.setSkuName("Google Workspace Business Standard");
        sub2.setStatus("ACTIVE");
        sub2.setPlanType("ANNUAL");
        sub2.setTotalLicenses(5);
        subscriptions.add(sub2);

        response.setSubscriptions(subscriptions);
        return response;
    }

    /**
     * Create a sample JSON response for subscriptions
     */
    public static JsonNode createSubscriptionsJsonResponse() {
        ObjectNode responseNode = JsonNodeFactory.instance.objectNode();
        ArrayNode subscriptionsArray = JsonNodeFactory.instance.arrayNode();

        // Create first subscription
        ObjectNode subscription1 = JsonNodeFactory.instance.objectNode();
        subscription1.put("skuId", "sku-1");
        subscription1.put("skuName", "Business Starter");
        subscription1.put("status", "ACTIVE");

        // Add plan info
        ObjectNode plan1 = JsonNodeFactory.instance.objectNode();
        plan1.put("planName", "ANNUAL");
        subscription1.set("plan", plan1);

        // Add seats info
        ObjectNode seats1 = JsonNodeFactory.instance.objectNode();
        seats1.put("numberOfSeats", 10);
        subscription1.set("seats", seats1);

        // Create second subscription
        ObjectNode subscription2 = JsonNodeFactory.instance.objectNode();
        subscription2.put("skuId", "sku-2");
        subscription2.put("skuName", "Business Standard");
        subscription2.put("status", "ACTIVE");

        // Add plan info
        ObjectNode plan2 = JsonNodeFactory.instance.objectNode();
        plan2.put("planName", "ANNUAL");
        subscription2.set("plan", plan2);

        // Add seats info
        ObjectNode seats2 = JsonNodeFactory.instance.objectNode();
        seats2.put("numberOfSeats", 5);
        subscription2.set("seats", seats2);

        // Add subscriptions to array
        subscriptionsArray.add(subscription1);
        subscriptionsArray.add(subscription2);

        // Add array to response
        responseNode.set("subscriptions", subscriptionsArray);

        return responseNode;
    }
}