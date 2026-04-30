package com.example.payment_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Header-based authentication filter.
 * <p>
 * The api-gateway has already validated the JWT and resolved the user context;
 * it forwards everything we need via X-* headers. This filter just reads them,
 * runs business checks (verified / subscription), and populates SecurityContext.
 * <p>
 * IMPORTANT: this filter trusts X-* headers because in production the service
 * is reachable only from the gateway (network-level isolation via VPC/SG).
 * In dev there is no such guarantee — be aware when calling 8082 directly.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.filter", havingValue = "header", matchIfMissing = true)
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");

        // No X-User-Id → request is unauthenticated.
        // Pass through and let Spring Security decide (anonymous endpoints work, others get 401).
        if (userIdHeader == null || userIdHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // X-User-Id present → gateway authenticated this request, all other headers must be present
        String companyIdHeader       = request.getHeader("X-Company-Id");
        String email                 = request.getHeader("X-Email");
        String role                  = request.getHeader("X-Role");
        String companyVerifiedHeader = request.getHeader("X-Company-Verified");
        String subscriptionStatus    = request.getHeader("X-Subscription-Status");

        if (companyIdHeader == null || role == null) {
            log.warn("X-User-Id present but X-Company-Id or X-Role missing — malformed gateway request");
            writeUnauthorized(response, "Invalid authentication context");
            return;
        }

        UUID userId;
        UUID companyId;
        try {
            userId    = UUID.fromString(userIdHeader);
            companyId = UUID.fromString(companyIdHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in auth headers: {}", e.getMessage());
            writeUnauthorized(response, "Invalid authentication context");
            return;
        }

        boolean companyVerified = Boolean.parseBoolean(companyVerifiedHeader);

        // ── Business gates — same rules as old JwtAuthFilter ──────────────────
        String method = request.getMethod();
        boolean mutating = method.equals("POST") || method.equals("PUT")
                        || method.equals("DELETE") || method.equals("PATCH");

        if (!companyVerified && mutating) {
            log.warn("Company not verified, blocking mutating request: {} {}", method, request.getRequestURI());
            writePaymentRequired(response, "Company email not verified");
            return;
        }

        if (!"ACTIVE".equals(subscriptionStatus) && mutating) {
            writePaymentRequired(response, "Subscription required");
            return;
        }

        // ── Build SecurityContext ─────────────────────────────────────────────
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthenticatedUser principal = new AuthenticatedUser(
                    userId,
                    companyId,
                    email,
                    role,
                    companyVerified,
                    subscriptionStatus
            );

            log.debug("Authenticated via headers: userId={}, role={}", userId, role);

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

    private void writePaymentRequired(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":\"error\",\"message\":\"" + message + "\",\"data\":null}"
        );
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
