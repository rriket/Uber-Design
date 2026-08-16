package com.uberclone.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts realm_access.roles and resource_access.*.roles from a Keycloak JWT
 * and maps them to Spring Security authorities with the ROLE_ prefix.
 */
public class KeycloakJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter =
            new JwtGrantedAuthoritiesConverter();

    @Override
    @SuppressWarnings("unchecked")
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new HashSet<>(
                Optional.ofNullable(defaultConverter.convert(jwt)).orElse(Collections.emptyList()));

        // Realm roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            roles.stream()
                    .map(Object::toString)
                    .map(r -> "ROLE_" + r)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        // Resource (client) roles
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().stream()
                    .filter(v -> v instanceof Map)
                    .map(v -> (Map<String, Object>) v)
                    .flatMap(m -> {
                        Object rs = m.get("roles");
                        return (rs instanceof Collection<?>) ? ((Collection<?>) rs).stream() : Stream.empty();
                    })
                    .map(Object::toString)
                    .map(r -> "ROLE_" + r)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        String principal = jwt.getClaimAsString("preferred_username");
        if (principal == null) {
            principal = jwt.getSubject();
        }
        return new JwtAuthenticationToken(jwt, authorities, principal);
    }
}
