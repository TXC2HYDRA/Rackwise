package com.capacitydesk.identity.api;

import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.AuditResult;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.identity.application.TokenService;
import com.capacitydesk.identity.application.UserService;
import com.capacitydesk.identity.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {}

    record LoginResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {}

    private final UserService users;
    private final TokenService tokens;
    private final AuditService audit;

    AuthController(UserService users, TokenService tokens, AuditService audit) {
        this.users = users;
        this.tokens = tokens;
        this.audit = audit;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest req) {
        User user;
        try {
            user = users.authenticate(req.username(), req.password());
        } catch (BadCredentialsException e) {
            audit.recordIndependent(AuditAction.LOGIN, TargetType.USER, req.username(), AuditResult.FAILURE, null, null, "bad credentials");
            throw e;
        }
        audit.recordAs(user.getId(), user.getUsername(), AuditAction.LOGIN, TargetType.USER, user.getId().toString());
        TokenService.Token t = tokens.issue(user);
        return new LoginResponse(t.value(), "Bearer", t.expiresInSeconds(), UserDto.from(user));
    }

    @GetMapping("/me")
    UserDto me(Authentication auth) {
        return UserDto.from(users.get(UUID.fromString(auth.getName())));
    }
}
