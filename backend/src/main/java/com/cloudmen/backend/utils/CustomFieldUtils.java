package com.cloudmen.backend.utils;

import java.util.Map;

public class CustomFieldUtils {

    /**
     * Get the value of a custom field from a Teamleader object
     * 
     * @param customFields The custom fields map from the Teamleader object
     * @param fieldId      The ID of the custom field to get
     * @return The value of the custom field, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static Object getCustomFieldValue(Map<String, Object> customFields, String fieldId) {
        if (customFields == null || fieldId == null) {
            return null;
        }

        Object field = customFields.get(fieldId);
        if (!(field instanceof Map)) {
            return null;
        }

        Map<String, Object> fieldMap = (Map<String, Object>) field;
        return fieldMap.get("value");
    }

    /**
     * Check if a boolean custom field is true
     * 
     * @param customFields The custom fields map from the Teamleader object
     * @param fieldId      The ID of the custom field to check
     * @return true if the field exists and its value is explicitly true, false
     *         otherwise
     */
    public static boolean isCustomFieldTrue(Map<String, Object> customFields, String fieldId) {
        Object value = getCustomFieldValue(customFields, fieldId);
        // Only return true if the value is explicitly Boolean.TRUE
        return Boolean.TRUE.equals(value);
    }
}