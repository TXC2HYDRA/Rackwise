package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.identity.application.UserService;
import com.capacitydesk.identity.domain.Role;
import com.capacitydesk.inventory.application.InventoryService;
import com.capacitydesk.inventory.application.InventoryService.NewServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
abstract class IntegrationTestBase {

    static final String PASSWORD = "Passw0rd!-test";

    record TestUser(UUID id, String username, String token) {}

    record Fixture(UUID dcId, String dcCode, UUID rgId) {}

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired UserService users;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;

    Api api;

    @BeforeEach
    void initApi() {
        api = new Api(port, json);
    }

    static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    TestUser user(Role... roles) {
        String name = "u" + unique();
        var u = users.create(name, PASSWORD, "Test " + name, Set.of(roles));
        return new TestUser(u.getId(), name, login(name, PASSWORD));
    }

    String login(String username, String password) {
        Api.Result r = api.post("/auth/login", null, Map.of("username", username, "password", password));
        assertThat(r.status()).as("login of %s", username).isEqualTo(200);
        return r.body().path("accessToken").asString();
    }

    /** A private data center with one rack group "X", pre-filled with {@code existing} 500 W servers. */
    Fixture fixture(int slots, long watts, int existing) {
        String code = "T" + unique().toUpperCase();
        var dc = inventory.createDataCenter(code, "Test DC " + code);
        var rg = inventory.createRackGroup(dc.id(), "X", "Rack X", slots, watts);
        for (int i = 1; i <= existing; i++) {
            inventory.addServer(new NewServer("%s-x-%04d".formatted(code.toLowerCase(), i), rg.id(), 32, 256, 8, 500));
        }
        return new Fixture(dc.id(), code, rg.id());
    }

    Map<String, Object> requestBody(Fixture f, int servers, boolean spill) {
        return Map.of("dataCenterId", f.dcId().toString(), "preferredRackGroupId", f.rgId().toString(), "allowOtherRackGroups", spill,
                "serverCount", servers, "cpuCores", 32, "ramGb", 256, "storageTb", 8, "powerWatts", 500,
                "justification", "integration test", "priority", "HIGH");
    }

    JsonNode createRequest(TestUser requester, Fixture f, int servers) {
        Api.Result r = api.post("/capacity-requests", requester.token(), requestBody(f, servers, false));
        assertThat(r.status()).as("create request: %s", r.body()).isEqualTo(201);
        return r.body();
    }

    JsonNode plan(TestUser requester, JsonNode request) {
        Api.Result r = api.post("/capacity-requests/" + request.path("id").asString() + "/plans", requester.token(), null);
        assertThat(r.status()).as("plan: %s", r.body()).isEqualTo(201);
        return r.body();
    }

    Api.Result approve(TestUser reviewer, JsonNode plan) {
        return api.post("/plans/" + plan.path("id").asString() + "/approve", reviewer.token(),
                Map.of("contentHash", plan.path("contentHash").asString(), "comment", "looks fine"));
    }

    Api.Result apply(TestUser who, JsonNode plan) {
        return api.post("/plans/" + plan.path("id").asString() + "/apply", who.token(), null);
    }

    JsonNode rackGroup(TestUser viewer, Fixture f) {
        return api.get("/inventory/rack-groups/" + f.rgId(), viewer.token()).body();
    }

    int used(TestUser viewer, Fixture f) {
        return rackGroup(viewer, f).path("slotsUsed").asInt();
    }

    int totalRequests(TestUser requester) {
        return api.get("/capacity-requests?size=100", requester.token()).body().path("totalElements").asInt();
    }

    <T> List<T> parallel(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) out.add(f.get());
        pool.shutdown();
        return out;
    }

    <T> List<T> parallel(int copies, Callable<T> task) throws Exception {
        return parallel(java.util.stream.IntStream.range(0, copies).mapToObj(i -> task).toList());
    }
}
