package com.cloudmen.backend.api.dtos.signaturesatori;

/**
 * Request DTO for purchasing SignatureSatori credits
 */
public class SignatureSatoriPurchaseRequestDTO {
    private int count;

    public SignatureSatoriPurchaseRequestDTO() {
    }

    public SignatureSatoriPurchaseRequestDTO(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}