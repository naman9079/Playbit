package com.playbit.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean JwtDecoder jwtDecoder(@Value("${playbit.supabase-url}") String url, @Value("${playbit.jwt-secret}") String secret) {
        NimbusJwtDecoder decoder = secret.isBlank()
            ? NimbusJwtDecoder.withJwkSetUri(url + "/auth/v1/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256).jwsAlgorithm(SignatureAlgorithm.RS256).build()
            : NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("authenticated") && "authenticated".equals(jwt.getClaimAsString("role"))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Authenticated user token required", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(url + "/auth/v1"), audience));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, @Value("${playbit.cors-origins}") String origins) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origins.split(",")));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", cors);
        return http.csrf(c -> c.disable()).cors(c -> c.configurationSource(source))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/health", "/api/subjects", "/api/subjects/*/topics").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> {})).build();
    }
}
