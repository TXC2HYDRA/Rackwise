package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.identity.domain.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class AuthSecurityIT extends IntegrationTestBase {

    private String token(JwtEncoder enc, TestUser u, String issuer, Instant exp, List<String> roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(u.id().toString()).issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(exp).claim("username", u.username()).claim("roles", roles).build();
        return enc.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void validLoginReturnsShortLivedTokenAndUser() {
        TestUser u = user(Role.REQUESTER);
        Api.Result r = api.post("/auth/login", null, Map.of("username", u.username(), "password", PASSWORD));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(r.body().path("expiresIn").asInt()).isEqualTo(900);
        assertThat(r.body().path("user").path("roles").get(0).asString()).isEqualTo("REQUESTER");
        assertThat(r.body().toString()).doesNotContain("passwordHash");
    }

    @Test
    void invalidCredentialsGet401WithSameMessageForUnknownAndWrongPassword() {
        TestUser u = user(Role.REQUESTER);
        Api.Result wrong = api.post("/auth/login", null, Map.of("username", u.username(), "password", "nope-nope"));
        Api.Result ghost = api.post("/auth/login", null, Map.of("username", "ghost-" + unique(), "password", "nope-nope"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(ghost.status()).isEqualTo(401);
        assertThat(wrong.body().path("message").asString()).isEqualTo(ghost.body().path("message").asString());
    }

    @Test
    void failedLoginIsAudited() {
        TestUser admin = user(Role.ADMIN);
        String name = "ghost-" + unique();
        api.post("/auth/login", null, Map.of("username", name, "password", "nope-nope"));
        Api.Result r = api.get("/audit-events?action=LOGIN&targetId=" + name, admin.token());
        assertThat(r.body().path("totalElements").asInt()).isEqualTo(1);
        assertThat(r.body().path("content").get(0).path("result").asString()).isEqualTo("FAILURE");
    }

    @Test
    void missingMalformedExpiredWrongKeyAndWrongIssuerTokensAreRejected() {
        TestUser u = user(Role.REQUESTER);
        assertThat(api.get("/auth/me", null).status()).isEqualTo(401);
        assertThat(api.get("/auth/me", null).code()).isEqualTo("UNAUTHENTICATED");
        assertThat(api.get("/auth/me", "abc.def.ghi").status()).isEqualTo(401);

        String expired = token(jwtEncoder, u, "rackwise", Instant.now().minusSeconds(60), List.of("REQUESTER"));
        assertThat(api.get("/auth/me", expired).status()).isEqualTo(401);

        JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec("a-completely-different-32-byte-secret!!".getBytes(), "HmacSHA256")));
        String forged = token(otherKey, u, "rackwise", Instant.now().plusSeconds(600), List.of("ADMIN"));
        assertThat(api.get("/users", forged).status()).isEqualTo(401);

        String wrongIssuer = token(jwtEncoder, u, "someone-else", Instant.now().plusSeconds(600), List.of("REQUESTER"));
        assertThat(api.get("/auth/me", wrongIssuer).status()).isEqualTo(401);

        String good = token(jwtEncoder, u, "rackwise", Instant.now().plusSeconds(600), List.of("REQUESTER"));
        assertThat(api.get("/auth/me", good).status()).isEqualTo(200);
    }

    @Test
    void requesterCannotReachAdminOrReviewerEndpointsEvenWithInvalidBodies() {
        TestUser req = user(Role.REQUESTER);
        assertThat(api.get("/users", req.token()).status()).isEqualTo(403);
        assertThat(api.get("/audit-events", req.token()).status()).isEqualTo(403);
        assertThat(api.get("/approvals/pending", req.token()).status()).isEqualTo(403);
        assertThat(api.post("/inventory/datacenters", req.token(), Map.of()).status()).isEqualTo(403);
        assertThat(api.post("/plans/" + java.util.UUID.randomUUID() + "/approve", req.token(), Map.of()).status()).isEqualTo(403);
        assertThat(api.get("/users", req.token()).code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void privilegeEscalationViaRoleEndpointFailsAndLeavesRolesUntouched() {
        TestUser req = user(Role.REQUESTER);
        Api.Result r = api.put("/users/" + req.id() + "/roles", req.token(), Map.of("roles", List.of("ADMIN")));
        assertThat(r.status()).isEqualTo(403);
        assertThat(users.get(req.id()).getRoles()).containsExactly(Role.REQUESTER);
    }

    @Test
    void adminManagesRolesButCannotDemoteSelf() {
        TestUser admin = user(Role.ADMIN);
        TestUser target = user(Role.REQUESTER);
        assertThat(api.put("/users/" + target.id() + "/roles", admin.token(), Map.of("roles", List.of("REVIEWER"))).status()).isEqualTo(200);
        assertThat(users.get(target.id()).getRoles()).containsExactly(Role.REVIEWER);
        Api.Result self = api.put("/users/" + admin.id() + "/roles", admin.token(), Map.of("roles", List.of("REQUESTER")));
        assertThat(self.status()).isEqualTo(409);
        assertThat(users.get(admin.id()).getRoles()).containsExactly(Role.ADMIN);
        assertThat(api.put("/users/" + target.id() + "/roles", admin.token(), Map.of("roles", List.of())).status()).isEqualTo(400);
    }

    @Test
    void clientCannotOverrideStatusOrRequester() {
        TestUser alice = user(Role.REQUESTER);
        TestUser other = user(Role.REQUESTER);
        Fixture f = fixture(20, 20_000, 0);
        var body = new java.util.HashMap<>(requestBody(f, 2, false));
        body.put("status", "FULFILLED");
        body.put("requesterId", other.id().toString());
        Api.Result r = api.post("/capacity-requests", alice.token(), body);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().path("status").asString()).isEqualTo("SUBMITTED");
        assertThat(r.body().path("requesterId").asString()).isEqualTo(alice.id().toString());
    }

    @Test
    void malformedJsonAndUnknownRoutesUseTheErrorContract() {
        TestUser u = user(Role.REQUESTER);
        Api.Result bad = api.post("/capacity-requests", u.token(), "{not json");
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().has("timestamp")).isTrue();
        assertThat(bad.body().has("path")).isTrue();
        assertThat(api.get("/does-not-exist", u.token()).status()).isEqualTo(404);
        assertThat(api.post("/auth/login", null, Map.of("username", "", "password", "")).status()).isEqualTo(400);
    }

    @Test
    void actuatorHealthIsPublicButPrometheusRequiresAdmin() {
        TestUser req = user(Role.REQUESTER);
        TestUser admin = user(Role.ADMIN);
        assertThat(api.call("GET", "http://localhost:" + port + "/actuator/health", null, null, Map.of()).status()).isEqualTo(200);
        assertThat(api.call("GET", "http://localhost:" + port + "/actuator/prometheus", null, null, Map.of()).status()).isEqualTo(401);
        assertThat(api.call("GET", "http://localhost:" + port + "/actuator/prometheus", req.token(), null, Map.of()).status()).isEqualTo(403);
        assertThat(api.call("GET", "http://localhost:" + port + "/actuator/prometheus", admin.token(), null, Map.of()).status()).isEqualTo(200);
    }
}
