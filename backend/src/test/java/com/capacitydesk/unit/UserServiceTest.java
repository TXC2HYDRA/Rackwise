package com.capacitydesk.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.identity.application.UserService;
import com.capacitydesk.identity.domain.Role;
import com.capacitydesk.identity.domain.User;
import com.capacitydesk.identity.persistence.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private UserRepository repo;
    private PasswordEncoder encoder;
    private AuditService audit;
    private UserService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        audit = mock(AuditService.class);
        when(encoder.encode(anyString())).thenReturn("{bcrypt}dummy");
        service = new UserService(repo, encoder, audit);
    }

    @Test
    void unknownUserStillRunsAPasswordComparisonAndIsRejected() {
        when(repo.findByUsername("ghost")).thenReturn(Optional.empty());
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.authenticate("ghost", "pw")).isInstanceOf(BadCredentialsException.class);
        verify(encoder).matches("pw", "{bcrypt}dummy");
    }

    @Test
    void wrongPasswordIsRejected() {
        User u = new User("alice", "{bcrypt}hash", "Alice", Set.of(Role.REQUESTER));
        when(repo.findByUsername("alice")).thenReturn(Optional.of(u));
        when(encoder.matches("bad", "{bcrypt}hash")).thenReturn(false);
        assertThatThrownBy(() -> service.authenticate("alice", "bad")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void correctPasswordReturnsUser() {
        User u = new User("alice", "{bcrypt}hash", "Alice", Set.of(Role.REQUESTER));
        when(repo.findByUsername("alice")).thenReturn(Optional.of(u));
        when(encoder.matches("good", "{bcrypt}hash")).thenReturn(true);
        assertThat(service.authenticate("alice", "good")).isSameAs(u);
    }

    @Test
    void adminCannotRemoveOwnAdminRoleAndNothingIsSaved() {
        User admin = new User("root", "h", "Root", Set.of(Role.ADMIN));
        when(repo.findById(admin.getId())).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> service.setRoles(admin.getId(), admin.getId(), Set.of(Role.REVIEWER)))
                .isInstanceOf(DomainException.class).hasMessageContaining("own ADMIN role");
        assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
        verify(repo, never()).save(any());
    }

    @Test
    void adminCanChangeAnotherUsersRolesAndChangeIsAudited() {
        User admin = new User("root", "h", "Root", Set.of(Role.ADMIN));
        User bob = new User("bob", "h", "Bob", Set.of(Role.REQUESTER));
        when(repo.findById(bob.getId())).thenReturn(Optional.of(bob));
        when(repo.save(bob)).thenReturn(bob);
        service.setRoles(admin.getId(), bob.getId(), Set.of(Role.REVIEWER));
        assertThat(bob.getRoles()).containsExactly(Role.REVIEWER);
        verify(audit).success(any(), any(), any(), any(), any());
    }
}
