package com.recruitment.auth;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Diagnostic toi thieu cho chinh "Xong khi" cua FR-C01: chua co endpoint HR nghiep vu
// thuc nao o A1 (Company/Job la B1/B2), nhung tieu chi nghiem thu can mot endpoint that
// de doi chung du 3 truong hop (HR -> 200, candidate -> 403, khong token -> 401). Quyen
// truy cap da duoc chan o SecurityConfig bang path rule /api/hr/** -> hasRole("HR").
@RestController
public class HrPingController {

    @GetMapping("/api/hr/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
