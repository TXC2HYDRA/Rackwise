package com.capacitydesk.identity.api;

import com.capacitydesk.identity.application.UserService;
import com.capacitydesk.identity.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
class UserAdminController {

    record RolesRequest(@NotEmpty Set<Role> roles) {}

    private final UserService users;

    UserAdminController(UserService users) {
        this.users = users;
    }

    @GetMapping
    List<UserDto> list() {
        return users.list().stream().map(UserDto::from).toList();
    }

    @PutMapping("/{id}/roles")
    UserDto setRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest req, Authentication auth) {
        return UserDto.from(users.setRoles(UUID.fromString(auth.getName()), id, req.roles()));
    }
}
