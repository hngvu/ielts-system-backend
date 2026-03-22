package io.gsp26se16.moni.authentication.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/users/**",
        "/auth/token",
        "/credentials/register",
        "/credentials/change-password",
        "/auth/token",
        "/auth/refresh",
        "/auth/logout",
        "/auth/outbound/authentication",
        "/api/v1/ai/writing/score",
        "/api/v1/vocab/lookup",
        "/payments/sepay"
    };

    private final CustomJwtDecoder customJwtDecoder;

    public SecurityConfig(CustomJwtDecoder customJwtDecoder) {
        this.customJwtDecoder = customJwtDecoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeHttpRequests(request -> request
                // SEpay webhook — cho phép mọi method, không cần auth
                .requestMatchers("/payments/sepay")
                .permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS)
                .permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/experts",
                        "/api/v1/experts/**",
                        "/api/v1/vocab/lookup",
                        "/api/v1/vocab/browse",
                        "/api/v1/vocab/browse/**",
                        "/api/v1/vocab/bands",
                        "/api/v1/vocab/topics",
                        "/api/v1/vocab/quiz",
                        "/api/v1/vocab/word-match",
                        "/api/v1/vocab/enrich/status",
                        "/api/v1/vocab/search",
                        "/api/v1/tests",
                        "/api/v1/tests/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/vocab/enrich")
                .permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .permitAll()
                .anyRequest()
                .authenticated());

        httpSecurity.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(request -> {
                    String uri = request.getRequestURI();
                    String servletPath = request.getServletPath();
                    log.debug(
                            "[BearerTokenResolver] URI={}, servletPath={}, Authorization={}",
                            uri,
                            servletPath,
                            request.getHeader("Authorization"));
                    // SEpay webhook: trả null để Spring coi như anonymous
                    if (uri.startsWith("/payments/sepay") || servletPath.startsWith("/payments/sepay")) {
                        log.info("[BearerTokenResolver] Skipping JWT for SEpay webhook: {}", uri);
                        return null;
                    }
                    String bearer = request.getHeader("Authorization");
                    if (bearer != null && bearer.startsWith("Bearer ")) {
                        return bearer.substring(7);
                    }
                    return null;
                })
                .jwt(jwtConfigurer -> jwtConfigurer
                        .decoder(customJwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));
        httpSecurity.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        httpSecurity.csrf(AbstractHttpConfigurer::disable);

        return httpSecurity.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "https://moni-fe.vercel.app"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
