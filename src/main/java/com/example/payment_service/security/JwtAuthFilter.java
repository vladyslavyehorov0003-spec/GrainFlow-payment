package com.example.payment_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthClient authClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token — pass through as anonymous, let SecurityConfig decide access
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Token present — delegate validation entirely to auth-service
        ValidateResponse validated = authClient.validate(authHeader);

        if (!validated.valid()) {
            log.warn("Invalid or expired token for request: {}", request.getRequestURI());
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        // Token is valid — build principal and set SecurityContext
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthenticatedUser principal = new AuthenticatedUser(
                    validated.userId(),
                    validated.companyId(),
                    validated.email(),
                    validated.role()
            );
            log.debug("Authenticated user: email={}, role={}, authorities={}",
                    principal.email(), principal.role(), principal.getAuthorities());

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":\"error\",\"message\":\"" + message + "\",\"data\":null}"
        );
    }
}
