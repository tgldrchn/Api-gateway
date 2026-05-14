package com.example.demo.controller;

import com.example.demo.service.ProxyService;
import com.example.demo.service.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    @Autowired
    private ProxyService proxyService;

    @Autowired
    private RedisService redisService;

    // GET хүсэлт - Cache ашиглана
    @GetMapping("/api/**")
    public ResponseEntity<String> handleGet(HttpServletRequest request) {
        String cacheKey = request.getRequestURI() +
            (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        // Cache-аас хайх
        String cached = redisService.get(cacheKey);
        if (cached != null) {
            return ResponseEntity.ok()
                .header("X-Cache", "HIT")
                .body(cached);
        }

        // Backend-аас авах
        ResponseEntity<String> response = proxyService.forward(request, null);

        // Амжилттай бол cache-д хадгалах
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            redisService.set(cacheKey, response.getBody());
        }

        return ResponseEntity.status(response.getStatusCode())
            .header("X-Cache", "MISS")
            .body(response.getBody());
    }

    // POST хүсэлт - Cache ашиглахгүй
    @PostMapping("/api/**")
    public ResponseEntity<String> handlePost(HttpServletRequest request,
                                              @RequestBody(required = false) String body) {
        return proxyService.forward(request, body);
    }

    // PUT хүсэлт - Cache устгана
    @PutMapping("/api/**")
    public ResponseEntity<String> handlePut(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        redisService.delete(request.getRequestURI());
        return proxyService.forward(request, body);
    }

    // DELETE хүсэлт - Cache устгана
    @DeleteMapping("/api/**")
    public ResponseEntity<String> handleDelete(HttpServletRequest request) {
        redisService.delete(request.getRequestURI());
        return proxyService.forward(request, null);
    }

    // Health check
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"API Gateway is running\"}");
    }
}
