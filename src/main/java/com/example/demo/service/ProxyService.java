import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public ResponseEntity<String> forward(HttpServletRequest request, String body) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    String targetUrl = resolveTarget(path);

    if (targetUrl == null) {
        return ResponseEntity.status(404).body("{\"error\":\"No route found for: " + path + "\"}");
    }

    // ← Multipart request шалгах
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
                // Authorization header дамжуулах
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

    // Энгийн request
    HttpHeaders headers = copyHeaders(request);
    HttpEntity<String> entity = new HttpEntity<>(body, headers);

    try {
        return restTemplate.exchange(targetUrl, HttpMethod.valueOf(method), entity, String.class);
    } catch (Exception e) {
        log.error("Proxy error: {}", e.getMessage());
        return ResponseEntity.status(502).body("{\"error\":\"Backend error: " + e.getMessage() + "\"}");
    }
}