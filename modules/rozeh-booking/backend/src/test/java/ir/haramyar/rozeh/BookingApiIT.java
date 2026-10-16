package ir.haramyar.rozeh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The booking API through the same trust boundary the Hive BFF uses (forwarded token + X-Hive-User-Id), with Flyway,
 * JPA mapping, optimistic locking, the exclusion constraint, the outbox and broker delivery on real infrastructure.
 */
@Testcontainers
@SpringBootTest(properties = {
    "haramyar.security.issuer=http://issuer.test/realms/haramyar",
    "haramyar.security.jwk-set-uri=http://issuer.test/realms/haramyar/protocol/openid-connect/certs",
    "haramyar.profile=development",
    "haramyar.messaging.publisher.interval-ms=200",
    "haramyar.messaging.publisher.initial-delay-ms=200"})
@AutoConfigureMockMvc
class BookingApiIT {
  @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
  @Container @ServiceConnection static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1.4-management-alpine");

  static final UUID HOST_A = UUID.randomUUID();
  static final UUID HOST_B = UUID.randomUUID();
  static final UUID COORDINATOR = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired RabbitTemplate rabbit;

  @BeforeEach
  void clean() {
    db.update("alter table booking_history disable trigger booking_history_no_update");
    db.update("delete from booking_history");
    db.update("alter table booking_history enable trigger booking_history_no_update");
    db.update("delete from booking");
    db.update("delete from provider");
    db.update("delete from outbox_event");
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID user, String subject, String... realmRoles) {
    return request.header("X-Hive-User-Id", user.toString())
        .with(jwt().jwt(j -> j.subject(subject).issuer("http://issuer.test/realms/haramyar").claim("azp", "haramyar-web")
            .claim("preferred_username", "+98912000" + subject.hashCode() % 1000).claim("realm_access", Map.of("roles", List.of(realmRoles)))));
  }

  private MockHttpServletRequestBuilder asHost(MockHttpServletRequestBuilder request, UUID host) { return as(request, host, "kc-" + host, "haramyar-customer"); }
  private MockHttpServletRequestBuilder asCoordinator(MockHttpServletRequestBuilder request) { return as(request, COORDINATOR, "kc-coordinator", "haramyar-staff"); }

  private MockHttpServletResponse call(MockHttpServletRequestBuilder request, Object body) throws Exception {
    if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request).andReturn().getResponse();
  }

  private JsonNode ok(MockHttpServletResponse response, int status) throws Exception {
    assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
    return json.readTree(response.getContentAsString());
  }

  private Map<String, Object> submission(UUID clientRequestId, OffsetDateTime start) {
    var body = new java.util.HashMap<String, Object>();
    body.put("clientRequestId", clientRequestId);
    body.put("hostName", "Fixture Host");
    body.put("hostPhone", "09121234567");
    body.put("city", "Tehran");
    body.put("area", "Fixture area");
    body.put("address", "Fixture street 7");
    body.put("category", "rozeh");
    body.put("audience", "family");
    body.put("deliveryMode", "in-person");
    body.put("approximateCapacity", 30);
    body.put("startsAt", start.toString());
    body.put("durationMinutes", 90);
    body.put("timeZone", "Asia/Tehran");
    return body;
  }

  private static OffsetDateTime inDays(int days) {
    return OffsetDateTime.now(ZoneOffset.ofHoursMinutes(3, 30)).plusDays(days).truncatedTo(ChronoUnit.HOURS);
  }

  private String submit(UUID host, OffsetDateTime start) throws Exception {
    return ok(call(asHost(post("/my/bookings"), host), submission(UUID.randomUUID(), start)), 201).path("id").asText();
  }

  @Test
  void anonymousCallersReadPublicCatalogButCannotBook() throws Exception {
    JsonNode catalog = ok(call(get("/public/catalog"), null), 200);
    assertThat(catalog.path("categories").toString()).contains("rozeh");
    assertThat(catalog.path("provisional").asBoolean()).isTrue();
    MockHttpServletResponse anonymous = call(post("/my/bookings"), submission(UUID.randomUUID(), inDays(3)));
    assertThat(anonymous.getStatus()).isEqualTo(401);
    assertThat(call(get("/my/bookings"), null).getStatus()).isEqualTo(401);
  }

  @Test
  void forgedHiveUserHeaderWithoutCredentialIsRejected() throws Exception {
    MockHttpServletResponse forged = call(post("/my/bookings").header("X-Hive-User-Id", HOST_A.toString()), submission(UUID.randomUUID(), inDays(3)));
    assertThat(forged.getStatus()).isEqualTo(401);
    assertThat(forged.getContentAsString()).contains("IDENTITY_CONTEXT_INVALID");
  }

  @Test
  void submissionPersistsWithHistoryAndOutboxAtomically() throws Exception {
    JsonNode booking = ok(call(asHost(post("/my/bookings"), HOST_A), submission(UUID.randomUUID(), inDays(4))), 201);
    assertThat(booking.path("status").asText()).isEqualTo("REQUESTED");
    assertThat(booking.path("trackingCode").asText()).matches("RZ-[0-9A-Z]{4}-[0-9A-Z]{4}");
    assertThat(booking.path("host").path("phone").asText()).isEqualTo("+989121234567");
    assertThat(booking.path("host").path("hostId").asText()).isEqualTo(HOST_A.toString());
    UUID id = UUID.fromString(booking.path("id").asText());
    assertThat(db.queryForObject("select count(*) from booking_history where booking_id = ?", Integer.class, id)).isEqualTo(1);
    assertThat(db.queryForObject("select event_type from outbox_event where aggregate_id = ?", String.class, id.toString())).isEqualTo("haramyar.booking.requested.v1");
    String payload = db.queryForObject("select payload::text from outbox_event where aggregate_id = ?", String.class, id.toString());
    assertThat(payload).contains(HOST_A.toString()).doesNotContain("Fixture Host", "+98912", "Fixture street");
  }

  @Test
  void identityComesFromTheSecuredRequestNotTheBody() throws Exception {
    Map<String, Object> body = submission(UUID.randomUUID(), inDays(4));
    body.put("hostId", HOST_B.toString());
    body.put("status", "CONFIRMED");
    JsonNode booking = ok(call(asHost(post("/my/bookings"), HOST_A), body), 201);
    assertThat(booking.path("host").path("hostId").asText()).isEqualTo(HOST_A.toString());
    assertThat(booking.path("status").asText()).isEqualTo("REQUESTED");
  }

  @Test
  void duplicateAndConcurrentDuplicateSubmissionsCreateOneBooking() throws Exception {
    UUID key = UUID.randomUUID();
    Map<String, Object> body = submission(key, inDays(5));
    var pool = Executors.newFixedThreadPool(8);
    var barrier = new CyclicBarrier(8);
    List<Future<JsonNode>> results = new ArrayList<>();
    for (int i = 0; i < 8; i++) results.add(pool.submit(() -> { barrier.await(); return json.readTree(call(asHost(post("/my/bookings"), HOST_A), body).getContentAsString()); }));
    var ids = new java.util.HashSet<String>();
    for (var r : results) ids.add(r.get().path("id").asText());
    pool.shutdown();
    assertThat(ids).hasSize(1);
    assertThat(db.queryForObject("select count(*) from booking where client_request_id = ?", Integer.class, key)).isEqualTo(1);
    assertThat(db.queryForObject("select count(*) from outbox_event", Integer.class)).isEqualTo(1);
  }

  @Test
  void hostsSeeOnlyTheirOwnBookings() throws Exception {
    String mine = submit(HOST_A, inDays(3));
    submit(HOST_B, inDays(3));
    JsonNode list = ok(call(asHost(get("/my/bookings"), HOST_A), null), 200);
    assertThat(list.path("total").asLong()).isEqualTo(1);
    assertThat(list.path("items").get(0).path("id").asText()).isEqualTo(mine);
    assertThat(list.path("items").get(0).has("hostPhone")).isFalse();
    MockHttpServletResponse foreign = call(asHost(get("/my/bookings/" + mine), HOST_B), null);
    assertThat(foreign.getStatus()).isEqualTo(404);
    assertThat(call(asHost(post("/my/bookings/" + mine + "/cancel"), HOST_B), Map.of()).getStatus()).isEqualTo(404);
  }

  @Test
  void customersCannotUseCoordinationEvenByCallingItDirectly() throws Exception {
    String id = submit(HOST_A, inDays(3));
    MockHttpServletResponse escalation = call(asHost(post("/coordination/bookings/" + id + "/accept"), HOST_A), Map.of());
    assertThat(escalation.getStatus()).isEqualTo(403);
    assertThat(escalation.getContentAsString()).contains("STAFF_AUTHENTICATION_REQUIRED");
    assertThat(call(asHost(get("/coordination/bookings"), HOST_A), null).getStatus()).isEqualTo(403);
    assertThat(db.queryForObject("select status from booking where id = ?::uuid", String.class, id)).isEqualTo("REQUESTED");
  }

  @Test
  void lifecycleWithExplicitBusinessErrors() throws Exception {
    String id = submit(HOST_A, inDays(6));
    JsonNode skipped = ok(call(asHost(post("/my/bookings/" + id + "/confirm"), HOST_A), Map.of()), 409);
    assertThat(skipped.path("code").asText()).isEqualTo("BOOKING_INVALID_TRANSITION");
    JsonNode notPermitted = ok(call(asCoordinator(post("/coordination/bookings/" + id + "/confirm")), Map.of()), 403);
    assertThat(notPermitted.path("code").asText()).as("actor policy is evaluated before the transition").isEqualTo("BOOKING_ACTOR_NOT_PERMITTED");

    JsonNode accepted = ok(call(asCoordinator(post("/coordination/bookings/" + id + "/accept")), Map.of("expectedVersion", 0)), 200);
    assertThat(accepted.path("status").asText()).isEqualTo("ACCEPTED");
    JsonNode stale = ok(call(asHost(post("/my/bookings/" + id + "/confirm"), HOST_A), Map.of("expectedVersion", 0)), 409);
    assertThat(stale.path("code").asText()).isEqualTo("REVISION_CONFLICT");
    JsonNode coordinatorConfirm = ok(call(asCoordinator(post("/coordination/bookings/" + id + "/confirm")), Map.of()), 403);
    assertThat(coordinatorConfirm.path("code").asText()).isEqualTo("BOOKING_ACTOR_NOT_PERMITTED");

    JsonNode details = ok(call(asHost(get("/my/bookings/" + id), HOST_A), null), 200);
    assertThat(details.path("allowedActions").toString()).contains("CONFIRM", "CANCEL").doesNotContain("ACCEPT");
    JsonNode confirmed = ok(call(asHost(post("/my/bookings/" + id + "/confirm"), HOST_A), Map.of("expectedVersion", 1)), 200);
    assertThat(confirmed.path("status").asText()).isEqualTo("CONFIRMED");
    assertThat(confirmed.path("history").size()).isEqualTo(3);
    JsonNode again = ok(call(asCoordinator(post("/coordination/bookings/" + id + "/accept")), Map.of()), 409);
    assertThat(again.path("code").asText()).isEqualTo("BOOKING_INVALID_TRANSITION");
    JsonNode early = ok(call(asCoordinator(post("/coordination/bookings/" + id + "/complete")), Map.of()), 422);
    assertThat(early.path("code").asText()).isEqualTo("BOOKING_CEREMONY_NOT_STARTED");
    assertThat(db.queryForList("select event_type from outbox_event order by created_at", String.class)).containsExactly(
        "haramyar.booking.requested.v1", "haramyar.booking.accepted.v1", "haramyar.booking.confirmed.v1");
  }

  @Test
  void pastCeremonyAndInvalidInputAreRejected() throws Exception {
    JsonNode past = ok(call(asHost(post("/my/bookings"), HOST_A), submission(UUID.randomUUID(), OffsetDateTime.now().minusHours(1))), 422);
    assertThat(past.path("code").asText()).isEqualTo("BOOKING_CEREMONY_TIME_NOT_IN_FUTURE");
    Map<String, Object> badPhone = submission(UUID.randomUUID(), inDays(2));
    badPhone.put("hostPhone", "12345");
    assertThat(ok(call(asHost(post("/my/bookings"), HOST_A), badPhone), 400).path("code").asText()).isEqualTo("BOOKING_INVALID_VALUE");
    Map<String, Object> missingCity = submission(UUID.randomUUID(), inDays(2));
    missingCity.remove("city");
    assertThat(ok(call(asHost(post("/my/bookings"), HOST_A), missingCity), 400).path("code").asText()).isEqualTo("VALIDATION_FAILED");
    Map<String, Object> unknownAudience = submission(UUID.randomUUID(), inDays(2));
    unknownAudience.put("audience", "children");
    assertThat(ok(call(asHost(post("/my/bookings"), HOST_A), unknownAudience), 422).path("code").asText()).isEqualTo("BOOKING_CEREMONY_DETAILS_NOT_ALLOWED");
  }

  private String provider() throws Exception {
    return ok(call(asCoordinator(post("/coordination/providers")), Map.of("displayName", "Fixture Provider")), 201).path("id").asText();
  }

  private String acceptedWithProvider(UUID host, OffsetDateTime start, String provider) throws Exception {
    String id = submit(host, start);
    ok(call(asCoordinator(post("/coordination/bookings/" + id + "/accept")), Map.of()), 200);
    ok(call(asCoordinator(post("/coordination/bookings/" + id + "/provider")), Map.of("providerId", provider)), 200);
    return id;
  }

  @Test
  void concurrentConfirmationsCannotDoubleBookAProvider() throws Exception {
    String provider = provider();
    OffsetDateTime start = inDays(10);
    String first = acceptedWithProvider(HOST_A, start, provider);
    String second = acceptedWithProvider(HOST_B, start.plusMinutes(30), provider);
    var pool = Executors.newFixedThreadPool(2);
    var barrier = new CyclicBarrier(2);
    Callable<Integer> confirmFirst = () -> { barrier.await(); return call(asHost(post("/my/bookings/" + first + "/confirm"), HOST_A), Map.of()).getStatus(); };
    Callable<Integer> confirmSecond = () -> { barrier.await(); return call(asHost(post("/my/bookings/" + second + "/confirm"), HOST_B), Map.of()).getStatus(); };
    var a = pool.submit(confirmFirst);
    var b = pool.submit(confirmSecond);
    List<Integer> statuses = List.of(a.get(), b.get());
    pool.shutdown();
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(db.queryForObject("select count(*) from booking where status = 'CONFIRMED' and provider_id = ?::uuid", Integer.class, provider)).isEqualTo(1);
  }

  @Test
  void databaseRefusesOverlappingConfirmedSlotsEvenOutsideTheApplication() throws Exception {
    String provider = provider();
    OffsetDateTime start = inDays(12);
    String first = acceptedWithProvider(HOST_A, start, provider);
    String second = acceptedWithProvider(HOST_B, start.plusMinutes(10), provider);
    db.update("update booking set status = 'CONFIRMED' where id = ?::uuid", first);
    assertThatThrownBy(() -> db.update("update booking set status = 'CONFIRMED' where id = ?::uuid", second))
        .hasMessageContaining("booking_provider_confirmed_no_overlap");
    String adjacent = acceptedWithProvider(HOST_B, start.plusMinutes(90), provider);
    assertThat(ok(call(asHost(post("/my/bookings/" + adjacent + "/confirm"), HOST_B), Map.of()), 200).path("status").asText()).isEqualTo("CONFIRMED");
  }

  @Test
  void concurrentConfirmAndCancelHaveExactlyOneWinner() throws Exception {
    String id = submit(HOST_A, inDays(8));
    ok(call(asCoordinator(post("/coordination/bookings/" + id + "/accept")), Map.of()), 200);
    var pool = Executors.newFixedThreadPool(2);
    var barrier = new CyclicBarrier(2);
    var confirm = pool.submit(() -> { barrier.await(); return call(asHost(post("/my/bookings/" + id + "/confirm"), HOST_A), Map.of()).getStatus(); });
    var cancel = pool.submit(() -> { barrier.await(); return call(asCoordinator(post("/coordination/bookings/" + id + "/cancel")), Map.of("reason", "Provider unavailable")).getStatus(); });
    List<Integer> statuses = List.of(confirm.get(), cancel.get());
    pool.shutdown();
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    String status = db.queryForObject("select status from booking where id = ?::uuid", String.class, id);
    assertThat(status).isIn("CONFIRMED", "CANCELLED");
    assertThat(db.queryForObject("select count(*) from booking_history where booking_id = ?::uuid", Integer.class, id)).isEqualTo(3);
  }

  @Test
  void historyIsAppendOnly() throws Exception {
    String id = submit(HOST_A, inDays(3));
    assertThatThrownBy(() -> db.update("update booking_history set detail = 'tampered' where booking_id = ?::uuid", id)).hasMessageContaining("append-only");
    assertThatThrownBy(() -> db.update("delete from booking_history where booking_id = ?::uuid", id)).hasMessageContaining("append-only");
  }

  @Test
  void outboxEventsReachTheBroker() throws Exception {
    RabbitAdmin admin = new RabbitAdmin(rabbit.getConnectionFactory());
    Queue probe = new Queue("test.booking.events", false, false, true);
    admin.declareQueue(probe);
    admin.declareBinding(new Binding(probe.getName(), Binding.DestinationType.QUEUE, "haramyar.events", "haramyar.booking.#", null));
    String id = submit(HOST_A, inDays(3));
    await().atMost(Duration.ofSeconds(15)).until(() -> "PUBLISHED".equals(db.queryForObject("select status from outbox_event where aggregate_id = ?", String.class, id)));
    var message = rabbit.receive(probe.getName(), 5000);
    assertThat(message).isNotNull();
    assertThat(message.getMessageProperties().getType()).isEqualTo("haramyar.booking.requested.v1");
    JsonNode envelope = json.readTree(message.getBody());
    assertThat(envelope.path("aggregateId").asText()).isEqualTo(id);
    assertThat(envelope.path("payload").path("trackingCode").asText()).startsWith("RZ-");
  }
}
