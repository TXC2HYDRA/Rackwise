package com.capacitydesk.identity.application;

import com.capacitydesk.identity.domain.Role;
import com.capacitydesk.identity.domain.User;
import com.capacitydesk.security.AppProperties;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    public record Token(String value, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public Token issue(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(Role::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().ttl()))
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(jwt, props.jwt().ttl().toSeconds());
    }
}
