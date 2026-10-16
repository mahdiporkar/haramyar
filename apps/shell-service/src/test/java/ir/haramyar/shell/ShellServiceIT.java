package ir.haramyar.shell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ir.haramyar.kit.messaging.RabbitTopology;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "haramyar.security.issuer=http://issuer.test/realms/haramyar",
    "haramyar.security.jwk-set-uri=http://issuer.test/realms/haramyar/protocol/openid-connect/certs",
    "haramyar.profile=development",
    "haramyar.hive.provisioning-password=test-only-provisioning-password-0001"})
@AutoConfigureMockMvc
class ShellServiceIT {
  @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
  @Container @ServiceConnection static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1.4-management-alpine");

  /** Stand-in for the Hive authorization service admin API (role assignment only). */
  static final HttpServer HIVE;
  static final List<String> HIVE_REQUESTS = new CopyOnWriteArrayList<>();
  static final AtomicInteger HIVE_STATUS = new AtomicInteger(201);

  static {
    try {
      HIVE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      HIVE.createContext("/admin/roles/haramyar-user/assignments", exchange -> {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        HIVE_REQUESTS.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Authorization") + " " + body);
        byte[] response = "{}".getBytes();
        exchange.sendResponseHeaders(HIVE_STATUS.get(), response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
      });
      HIVE.start();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void hive(DynamicPropertyRegistry registry) {
    registry.add("haramyar.hive.authorization-url", () -> "http://127.0.0.1:" + HIVE.getAddress().getPort());
  }

  @AfterAll static void stopHive() { HIVE.stop(0); }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired RabbitTemplate rabbit;

  @BeforeEach
  void clean() {
    db.update("delete from occasion");
    db.update("delete from onboarded_user");
    db.update("delete from notification");
    db.update("delete from inbox_message");
    HIVE_REQUESTS.clear();
    HIVE_STATUS.set(201);
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID user, String... roles) {
    return request.header("X-Hive-User-Id", user.toString()).with(jwt().jwt(j -> j.subject("kc-" + user).issuer("http://issuer.test/realms/haramyar")
        .claim("azp", "haramyar-web").claim("realm_access", Map.of("roles", List.of(roles)))));
  }

  private MockHttpServletResponse call(MockHttpServletRequestBuilder request, Object body) throws Exception {
    if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request).andReturn().getResponse();
  }

  private JsonNode ok(MockHttpServletResponse response, int status) throws Exception {
    assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
    return response.getContentAsString().isEmpty() ? null : json.readTree(response.getContentAsString());
  }

  @Test
  void onboardingAssignsOnlyTheDefaultRoleToTheCallerOnce() throws Exception {
    assertThat(call(post("/onboarding"), null).getStatus()).isEqualTo(401);
    UUID user = UUID.randomUUID();
    assertThat(ok(call(as(post("/onboarding"), user, "haramyar-customer"), Map.of("role", "haramyar-admin", "userId", UUID.randomUUID())), 200)
        .path("status").asText()).isEqualTo("ONBOARDED");
    assertThat(HIVE_REQUESTS).singleElement().satisfies(r -> {
      assertThat(r).startsWith("POST Basic ");
      assertThat(r).contains("\"subject\":\"user:" + user + "\"").doesNotContain("haramyar-admin");
    });
    assertThat(ok(call(as(post("/onboarding"), user, "haramyar-customer"), null), 200).path("status").asText()).isEqualTo("ALREADY_ONBOARDED");
    assertThat(HIVE_REQUESTS).hasSize(1);
  }

  @Test
  void onboardingIsIdempotentWhenHiveAlreadyHasTheAssignmentAndReportsOutages() throws Exception {
    HIVE_STATUS.set(409);
    assertThat(ok(call(as(post("/onboarding"), UUID.randomUUID(), "haramyar-customer"), null), 200).path("status").asText()).isEqualTo("ALREADY_ONBOARDED");
    HIVE_STATUS.set(500);
    UUID unlucky = UUID.randomUUID();
    assertThat(ok(call(as(post("/onboarding"), unlucky, "haramyar-customer"), null), 503).path("code").asText()).isEqualTo("ONBOARDING_UNAVAILABLE");
    assertThat(db.queryForObject("select count(*) from onboarded_user where hive_user_id = ?", Integer.class, unlucky)).isZero();
  }

  @Test
  void themeFollowsScheduledOccasionsByPriorityAndAdministrationNeedsStaff() throws Exception {
    assertThat(ok(call(get("/public/theme"), null), 200).path("theme").asText()).isEqualTo("default");
    UUID admin = UUID.randomUUID();
    Map<String, Object> mourning = Map.of("key", "fixture-mourning", "theme", "MOURNING", "titles", Map.of("fa", "عنوان نمونه", "en", "Fixture title"),
        "startsAt", Instant.now().minusSeconds(3600).toString(), "endsAt", Instant.now().plusSeconds(3600).toString(), "priority", 10);
    assertThat(ok(call(as(post("/admin/occasions"), UUID.randomUUID(), "haramyar-customer"), mourning), 403).path("code").asText()).isEqualTo("STAFF_AUTHENTICATION_REQUIRED");
    String id = ok(call(as(post("/admin/occasions"), admin, "haramyar-staff"), mourning), 201).path("id").asText();
    assertThat(ok(call(get("/public/theme"), null), 200).path("theme").asText()).as("drafts do not apply").isEqualTo("default");
    ok(call(as(post("/admin/occasions/" + id + "/schedule"), admin, "haramyar-staff"), null), 200);
    JsonNode theme = ok(call(get("/public/theme"), null), 200);
    assertThat(theme.path("theme").asText()).isEqualTo("mourning");
    assertThat(theme.path("occasion").path("titles").path("fa").asText()).isEqualTo("عنوان نمونه");

    Map<String, Object> celebration = Map.of("key", "fixture-celebration", "theme", "CELEBRATION", "titles", Map.of("fa", "جشن نمونه"),
        "startsAt", Instant.now().minusSeconds(60).toString(), "endsAt", Instant.now().plusSeconds(600).toString(), "priority", 5);
    String low = ok(call(as(post("/admin/occasions"), admin, "haramyar-staff"), celebration), 201).path("id").asText();
    ok(call(as(post("/admin/occasions/" + low + "/schedule"), admin, "haramyar-staff"), null), 200);
    assertThat(ok(call(get("/public/theme"), null), 200).path("theme").asText()).as("higher priority wins").isEqualTo("mourning");
    ok(call(as(post("/admin/occasions/" + id + "/archive"), admin, "haramyar-staff"), null), 200);
    assertThat(ok(call(get("/public/theme"), null), 200).path("theme").asText()).isEqualTo("celebration");

    Map<String, Object> invalid = Map.of("key", "fixture-invalid", "theme", "DEFAULT", "titles", Map.of("en", "No Persian title"),
        "startsAt", Instant.now().toString(), "endsAt", Instant.now().minusSeconds(5).toString(), "priority", 1);
    assertThat(ok(call(as(post("/admin/occasions"), admin, "haramyar-staff"), invalid), 400).path("code").asText()).isEqualTo("VALIDATION_FAILED");
  }

  private void publish(UUID eventId, String type, UUID host, UUID booking) throws Exception {
    String body = json.writeValueAsString(Map.of("eventId", eventId, "type", type, "aggregateType", "RozehBooking", "aggregateId", booking.toString(),
        "occurredAt", Instant.now().toString(), "payload", Map.of("bookingId", booking.toString(), "hostId", host.toString(), "trackingCode", "RZ-AB12-CD34")));
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(eventId.toString());
    properties.setType(type);
    rabbit.send(RabbitTopology.EXCHANGE, type, new Message(body.getBytes(StandardCharsets.UTF_8), properties));
  }

  @Test
  void bookingEventsBecomeExactlyOneNotificationForTheirHostOnly() throws Exception {
    UUID host = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    UUID booking = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    publish(eventId, "haramyar.booking.accepted.v1", host, booking);
    publish(eventId, "haramyar.booking.accepted.v1", host, booking);
    publish(UUID.randomUUID(), "haramyar.booking.confirmed.v1", host, booking);
    await().atMost(Duration.ofSeconds(15)).until(() -> db.queryForObject("select count(*) from notification", Integer.class) == 2);
    Thread.sleep(500);
    assertThat(db.queryForObject("select count(*) from notification where event_id = ?", Integer.class, eventId)).isEqualTo(1);

    JsonNode mine = ok(call(as(get("/notifications"), host, "haramyar-customer"), null), 200);
    assertThat(mine.path("unread").asInt()).isEqualTo(2);
    assertThat(mine.path("items").get(0).path("kind").asText()).isEqualTo("BOOKING_CONFIRMED");
    String notification = mine.path("items").get(0).path("id").asText();
    assertThat(ok(call(as(get("/notifications"), other, "haramyar-customer"), null), 200).path("items").size()).isZero();
    assertThat(call(as(post("/notifications/" + notification + "/read"), other, "haramyar-customer"), null).getStatus()).isEqualTo(404);
    assertThat(call(as(post("/notifications/" + notification + "/read"), host, "haramyar-customer"), null).getStatus()).isEqualTo(204);
    assertThat(ok(call(as(get("/notifications"), host, "haramyar-customer"), null), 200).path("unread").asInt()).isEqualTo(1);
  }

  @Test
  void unsupportedContractsAreDeadLetteredNotRetriedForever() throws Exception {
    publish(UUID.randomUUID(), "haramyar.booking.teleported.v1", UUID.randomUUID(), UUID.randomUUID());
    String dlq = RabbitTopology.deadLetterQueue(ShellServiceApplication.NOTIFICATION_QUEUE);
    await().atMost(Duration.ofSeconds(15)).until(() -> {
      Integer depth = rabbit.execute(channel -> channel.queueDeclarePassive(dlq).getMessageCount());
      return depth != null && depth >= 1;
    });
    assertThat(db.queryForObject("select count(*) from notification", Integer.class)).isZero();
  }
}
