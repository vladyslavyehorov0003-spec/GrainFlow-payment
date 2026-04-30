package com.example.payment_service.config;


import com.example.payment_service.security.HeaderAuthFilter;
import com.example.payment_service.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Optional;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Optional<JwtAuthFilter> jwtAuthFilter;
    private final Optional<HeaderAuthFilter> headerAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        OncePerRequestFilter activeAuthFilter = headerAuthFilter
                .map(f -> (OncePerRequestFilter) f)
                .or(() -> jwtAuthFilter.map(f -> (OncePerRequestFilter) f))
                .orElseThrow(() -> new IllegalStateException(
                        "No auth filter configured. Set auth.filter=header (recommended) or auth.filter=jwt."
                ));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/payments/webhook").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(activeAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
