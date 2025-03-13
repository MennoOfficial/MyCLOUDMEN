package com.cloudmen.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/**
 * Wrapper for HttpServletResponse to capture the status code
 */
public class AuthenticationResponseWrapper extends HttpServletResponseWrapper {

    private int status;

    public AuthenticationResponseWrapper(HttpServletResponse response) {
        super(response);
        this.status = 200; // Default to OK
    }

    @Override
    public void setStatus(int status) {
        super.setStatus(status);
        this.status = status;
    }

    @Override
    public void sendError(int sc) throws IOException {
        super.sendError(sc);
        this.status = sc;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        super.sendError(sc, msg);
        this.status = sc;
    }

    /**
     * Get the response status code
     * 
     * @return The HTTP status code
     */
    public int getStatus() {
        return status;
    }
}
