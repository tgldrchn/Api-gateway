package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);

    @Value("${upstash.redis.url}")
    private String redisUrl;

    @Value("${upstash.redis.token}")
    private String redisToken;

    private final RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + redisToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // Cache-аас авах
    public String get(String key) {
        try {
            String url = redisUrl + "/get/" + encode(key);
            HttpEntity<Void> entity = new HttpEntity<>(getHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            if (body != null && body.contains("\"result\":\"")) {
                int start = body.indexOf("\"result\":\"") + 10;
                int end = body.lastIndexOf("\"");
                String value = body.substring(start, end);
                log.info("✅ CACHE HIT: {}", key);
                return value;
            }
            log.info("❌ CACHE MISS: {}", key);
            return null;
        } catch (Exception e) {
            log.error("Redis GET error: {}", e.getMessage());
            return null;
        }
    }

    // Cache-д хадгалах (60 секунд TTL)
    public void set(String key, String value) {
        try {
            String url = redisUrl + "/set/" + encode(key) + "/" + encode(value) + "/ex/60";
            HttpEntity<Void> entity = new HttpEntity<>(getHeaders());
            restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("💾 CACHE SET: {}", key);
        } catch (Exception e) {
            log.error("Redis SET error: {}", e.getMessage());
        }
    }

    // Cache устгах
    public void delete(String key) {
        try {
            String url = redisUrl + "/del/" + encode(key);
            HttpEntity<Void> entity = new HttpEntity<>(getHeaders());
            restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("🗑️ CACHE DELETE: {}", key);
        } catch (Exception e) {
            log.error("Redis DELETE error: {}", e.getMessage());
        }
    }

    private String encode(String value) {
        return value.replace("/", "%2F").replace(" ", "%20");
    }
}
