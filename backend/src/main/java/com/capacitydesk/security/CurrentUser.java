package com.capacitydesk.security;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** The authenticated caller, derived only from the verified JWT. */
public record CurrentUser(UUID id, Set<String> roles) {

    public static CurrentUser from(Authentication auth) {
        return new CurrentUser(UUID.fromString(auth.getName()),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                        .map(a -> a.substring("ROLE_".length())).collect(Collectors.toSet()));
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    /** Reviewers and admins may see every request and plan. */
    public boolean canSeeAll() {
        return has("REVIEWER") || has("ADMIN");
    }
}
