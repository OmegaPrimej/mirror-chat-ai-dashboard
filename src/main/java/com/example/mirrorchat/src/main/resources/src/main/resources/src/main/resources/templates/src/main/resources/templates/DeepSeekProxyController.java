package com.example.mirrorchat.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Enumeration;

@RestController
@RequestMapping("/api/proxy")
public class DeepSeekProxyController {

    private final WebClient webClient;
    private final String DEEPSEEK_API_URL = "https://api.deepseek.com";

    public DeepSeekProxyController() {
        this.webClient = WebClient.builder()
                .baseUrl(DEEPSEEK_API_URL)
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public Mono<ResponseEntity<String>> proxy(HttpServletRequest request, @RequestBody(required = false) String body) {
        String path = request.getRequestURI().replace("/api/proxy", "");
        String query = request.getQueryString();
        String url = path + (query != null ? "?" + query : "");

        // Build headers – copy relevant ones (like Authorization)
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if ("authorization".equalsIgnoreCase(headerName) || "content-type".equalsIgnoreCase(headerName)) {
                headers.set(headerName, request.getHeader(headerName));
            }
        }

        // Make the request to DeepSeek
        return webClient.method(HttpMethod.valueOf(request.getMethod()))
                .uri(url)
                .headers(h -> h.addAll(headers))
                .bodyValue(body != null ? body : "")
                .retrieve()
                .toEntity(String.class)
                .map(response -> {
                    // Forward response with same status and headers
                    HttpHeaders responseHeaders = new HttpHeaders();
                    response.getHeaders().forEach((key, value) -> {
                        if (!"transfer-encoding".equalsIgnoreCase(key)) {
                            responseHeaders.put(key, value);
                        }
                    });
                    return new ResponseEntity<>(response.getBody(), responseHeaders, response.getStatusCode());
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(500).body("Proxy error: " + e.getMessage()));
                });
    }
}
