package ai.riskgraph.sandbox;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableMethodSecurity
public class SandboxApplication {
    public static void main(String[] args) { SpringApplication.run(SandboxApplication.class, args); }
    // Runtime sandbox configuration only; RiskGraph does NOT parse filter-chain authorization.
    @Bean SecurityFilterChain filters(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
    @RestController @Profile("protected")
    public static class ProtectedController {
        @GetMapping("/admin/export") @PreAuthorize("hasRole('ADMIN')")
        public Map<String,String> export() { return Map.of("customer_export", "sandbox-only"); }
    }
    @RestController @Profile("vulnerable")
    public static class VulnerableController {
        @GetMapping("/admin/export")
        public Map<String,String> export() { return Map.of("customer_export", "sandbox-only"); }
    }
}
