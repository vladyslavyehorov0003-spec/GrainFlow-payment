package com.example.payment_service.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Principal set in SecurityContext after successful token validation.
// Warehouse has no User entity — we work only with data returned from auth-service.
public record AuthenticatedUser(
        UUID userId,
        UUID companyId,
        String email,
        String role,
        boolean companyVerified,
        String subscriptionStatus
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String getPassword()   { return null; }
    @Override public String getUsername()   { return email != null ? email : userId.toString(); }
    @Override public boolean isEnabled()    { return true; }
}
