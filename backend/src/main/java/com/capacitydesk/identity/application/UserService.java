package com.capacitydesk.identity.application;

import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.identity.domain.Role;
import com.capacitydesk.identity.domain.User;
import com.capacitydesk.identity.persistence.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    /** Compared against when the username is unknown, so response time does not reveal valid usernames. */
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional(readOnly = true)
    public User authenticate(String username, String rawPassword) {
        User user = users.findByUsername(username).orElse(null);
        boolean ok = encoder.matches(rawPassword, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok || !user.isEnabled()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> DomainException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAll().stream().sorted((a, b) -> a.getUsername().compareTo(b.getUsername())).toList();
    }

    @Transactional
    public User setRoles(UUID actorId, UUID targetId, Set<Role> roles) {
        User target = get(targetId);
        if (actorId.equals(targetId) && !roles.contains(Role.ADMIN)) {
            throw DomainException.conflict("SELF_DEMOTION", "Administrators cannot remove their own ADMIN role");
        }
        target.replaceRoles(roles);
        return users.save(target);
    }

    @Transactional
    public User create(String username, String rawPassword, String displayName, Set<Role> roles) {
        return users.save(new User(username, encoder.encode(rawPassword), displayName, roles));
    }

    public boolean noUsers() {
        return users.count() == 0;
    }
}
