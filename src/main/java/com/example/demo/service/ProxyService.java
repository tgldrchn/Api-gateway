package com.example.demo.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Enumeration;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    @Value("${json.service.url}")
    private String jsonServiceUrl;

    @Value("${soap.service.url}")
    private String soapServiceUrl;

    @Value("${file.service.url}")
    private String fileServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public ResponseEntity<String> forward(HttpServletRequest request, String body) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String targetUrl = resolveTarget(path);

        if (targetUrl == null) {
            return ResponseEntity.status(404).body("{\"error\":\"No route found for: " + path + "\"}");
        }

        log.info("🔀 PROXY: {} {} -> {}", method, path, targetUrl);

        HttpHeaders headers = copyHeaders(request);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            return restTemplate.exchange(targetUrl, HttpMethod.valueOf(method), entity, String.class);
        } catch (Exception e) {
            log.error("Proxy error: {}", e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Backend error: " + e.getMessage() + "\"}");
        }
    }

    private String resolveTarget(String path) {
        if (path.startsWith("/api/users")) {
            return jsonServiceUrl + path.replace("/api/users", "/users");
        } else if (path.startsWith("/api/soap")) {
            return soapServiceUrl + path.replace("/api/soap", "/ws");
        } else if (path.startsWith("/api/files")) {
            return fileServiceUrl + path;
        }
        return null;
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!name.equalsIgnoreCase("host")) {
                headers.set(name, request.getHeader(name));
            }
        }
        return headers;
    }
}
