package com.example.demo.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import java.util.Enumeration;

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

        log.info("PROXY: {} {} -> {}", method, path, targetUrl);

        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            try {
                MultipartFile file = multipartRequest.getFile("file");
                if (file != null) {
                    MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
                    formData.add("file", new ByteArrayResource(file.getBytes()) {
                        @Override
                        public String getFilename() {
                            return file.getOriginalFilename();
                        }
                    });
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                    String auth = request.getHeader("Authorization");
                    if (auth != null) headers.set("Authorization", auth);
                    HttpEntity<MultiValueMap<String, Object>> entity =
                        new HttpEntity<>(formData, headers);
                    return restTemplate.exchange(targetUrl, HttpMethod.POST, entity, String.class);
                }
            } catch (Exception e) {
                log.error("Multipart proxy error: {}", e.getMessage());
                return ResponseEntity.status(502).body("{\"error\":\"File upload error: " + e.getMessage() + "\"}");
            }
        }

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
            if (!name.equalsIgnoreCase("host") &&
                !name.equalsIgnoreCase("accept-encoding")) {
                headers.set(name, request.getHeader(name));
            }
        }
        return headers;
    }
}