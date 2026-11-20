package com.capacitydesk.identity.application;

import com.capacitydesk.identity.domain.Role;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds demo accounts. Active only under the {@code dev} profile and only into an empty users table. */
@Component
@Profile("dev")
class DevUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserBootstrap.class);

    private final UserService users;
    private final String password;

    DevUserBootstrap(UserService users, @Value("${capacitydesk.dev-password}") String password) {
        this.users = users;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.noUsers()) {
            return;
        }
        users.create("alice", password, "Alice Requester", Set.of(Role.REQUESTER));
        users.create("bob", password, "Bob Reviewer", Set.of(Role.REVIEWER));
        users.create("carol", password, "Carol Reviewer", Set.of(Role.REVIEWER));
        users.create("admin", password, "Ada Admin", Set.of(Role.ADMIN));
        log.warn("Seeded DEV users alice/bob/carol/admin. Do not use the dev profile in production.");
    }
}
