package com.capacitydesk.identity.api;

import com.capacitydesk.identity.domain.Role;
import com.capacitydesk.identity.domain.User;
import java.util.List;
import java.util.UUID;

public record UserDto(UUID id, String username, String displayName, List<Role> roles) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getRoles().stream().sorted().toList());
    }
}
