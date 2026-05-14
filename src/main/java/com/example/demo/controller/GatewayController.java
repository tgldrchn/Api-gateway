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
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    @Autowired
    private ProxyService proxyService;

    @Autowired
    private RedisService redisService;

    // OPTIONS preflight - CORS-д зайлшгүй хэрэгтэй
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            .header("Access-Control-Allow-Headers", "*")
            .build();
    }

    // GET - cache ашиглана
    @GetMapping("/api/**")
    public ResponseEntity<String> handleGet(HttpServletRequest request) {
        String cacheKey = request.getRequestURI() +
            (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        String cached = redisService.get(cacheKey);
        if (cached != null) {
            return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("X-Cache", "HIT")
                .body(cached);
        }

        ResponseEntity<String> response = proxyService.forward(request, null);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            redisService.set(cacheKey, response.getBody());
        }

        return ResponseEntity.status(response.getStatusCode())
            .header("Access-Control-Allow-Origin", "*")
            .header("X-Cache", "MISS")
            .body(response.getBody());
    }

    // POST
    @PostMapping("/api/**")
    public ResponseEntity<String> handlePost(HttpServletRequest request,
                                              @RequestBody(required = false) String body) {
        ResponseEntity<String> response = proxyService.forward(request, body);
        return ResponseEntity.status(response.getStatusCode())
            .header("Access-Control-Allow-Origin", "*")
            .body(response.getBody());
    }

    // PUT
    @PutMapping("/api/**")
    public ResponseEntity<String> handlePut(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        redisService.delete(request.getRequestURI());
        ResponseEntity<String> response = proxyService.forward(request, body);
        return ResponseEntity.status(response.getStatusCode())
            .header("Access-Control-Allow-Origin", "*")
            .body(response.getBody());
    }

    // DELETE
    @DeleteMapping("/api/**")
    public ResponseEntity<String> handleDelete(HttpServletRequest request) {
        redisService.delete(request.getRequestURI());
        ResponseEntity<String> response = proxyService.forward(request, null);
        return ResponseEntity.status(response.getStatusCode())
            .header("Access-Control-Allow-Origin", "*")
            .body(response.getBody());
    }

    // Health check
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok()
            .header("Access-Control-Allow-Origin", "*")
            .body("{\"status\":\"API Gateway is running\"}");
    }
}