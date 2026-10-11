package com.membora.platform.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight compatibility health check for hosting platforms that expect /health. */
@RestController
public class HealthController {
    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
