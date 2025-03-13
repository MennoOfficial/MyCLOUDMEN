package com.cloudmen.backend.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Utility class for extracting information from HTTP requests
 */
public class RequestUtils {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
    };

    /**
     * Extract the client IP address from the request
     * 
     * @param request The HTTP request
     * @return The client IP address
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ipList = request.getHeader(header);
            if (ipList != null && ipList.length() != 0 && !"unknown".equalsIgnoreCase(ipList)) {
                // X-Forwarded-For can contain multiple IPs, the first one is the client
                String ip = ipList.split(",")[0].trim();
                if (isValidIp(ip)) {
                    return formatIpAddress(ip);
                }
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return formatIpAddress(remoteAddr);
    }

    /**
     * Format IP address for better readability
     * Converts IPv6 localhost to IPv4 format
     * 
     * @param ip The IP address to format
     * @return The formatted IP address
     */
    private static String formatIpAddress(String ip) {
        // Convert IPv6 localhost to IPv4 localhost for better readability
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    /**
     * Extract the user agent from the request
     * 
     * @param request The HTTP request
     * @return The user agent string
     */
    public static String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return StringUtils.hasText(userAgent) ? userAgent : "Unknown";
    }

    /**
     * Check if an IP address is valid
     * 
     * @param ip The IP address to check
     * @return True if the IP is valid, false otherwise
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // Simple validation for IPv4 and IPv6
        return ip.matches(
                "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")
                || ip.matches(
                        "^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$");
    }
}