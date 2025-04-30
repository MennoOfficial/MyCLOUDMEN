package com.cloudmen.backend.api.dtos.signaturesatori;

import java.time.ZonedDateTime;

/**
 * Response DTO for SignatureSatori credit purchase transactions
 */
public class SignatureSatoriPurchaseResponseDTO {
    private String type;
    private ZonedDateTime date;
    private int count;
    private String message;

    public SignatureSatoriPurchaseResponseDTO() {
    }

    public SignatureSatoriPurchaseResponseDTO(String type, ZonedDateTime date, int count, String message) {
        this.type = type;
        this.date = date;
        this.count = count;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public void setDate(ZonedDateTime date) {
        this.date = date;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}