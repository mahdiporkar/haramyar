package ir.haramyar.martyrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Editorial workflow, publication secrecy and media delivery on real PostgreSQL and S3-compatible storage (SeaweedFS). All profile content is a
 * clearly labelled synthetic development fixture.
 */
@Testcontainers
@SpringBootTest(properties = {
    "haramyar.security.issuer=http://issuer.test/realms/haramyar",
    "haramyar.security.jwk-set-uri=http://issuer.test/realms/haramyar/protocol/openid-connect/certs",
    "haramyar.profile=development"})
@AutoConfigureMockMvc
class MartyrsApiIT {
  @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
  static final String S3_CONFIG = "{\"identities\":[{\"name\":\"martyrs\",\"credentials\":[{\"accessKey\":\"test-access-key\",\"secretKey\":\"test-secret-key-0001\"}],\"actions\":[\"Admin\",\"Read\",\"Write\",\"List\"]}]}";
  @Container static final GenericContainer<?> S3 = new GenericContainer<>("chrislusf/seaweedfs:3.97")
      .withCopyToContainer(Transferable.of(S3_CONFIG), "/etc/seaweedfs/s3.json")
      .withCommand("server", "-filer", "-s3", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data", "-master.volumeSizeLimitMB=64", "-volume.max=4")
      .withExposedPorts(8333)
      .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403).withStartupTimeout(java.time.Duration.ofMinutes(2)));

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("haramyar.martyrs.storage.endpoint", () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(8333));
    registry.add("haramyar.martyrs.storage.access-key", () -> "test-access-key");
    registry.add("haramyar.martyrs.storage.secret-key", () -> "test-secret-key-0001");
  }

  static final UUID EDITOR = UUID.randomUUID();
  static final UUID REVIEWER = UUID.randomUUID();
  static final UUID CUSTOMER = UUID.randomUUID();
  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0};

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  @BeforeEach
  void clean() {
    db.update("delete from media_object");
    db.update("alter table martyr_editorial_history disable trigger martyr_history_no_update");
    db.update("delete from martyr_editorial_history");
    db.update("alter table martyr_editorial_history enable trigger martyr_history_no_update");
    db.update("delete from martyr_profile");
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID user, String... roles) {
    return request.header("X-Hive-User-Id", user.toString()).with(jwt().jwt(j -> j.subject("kc-" + user).issuer("http://issuer.test/realms/haramyar")
        .claim("azp", "haramyar-web").claim("realm_access", Map.of("roles", List.of(roles)))));
  }

  private MockHttpServletRequestBuilder editor(MockHttpServletRequestBuilder r) { return as(r, EDITOR, "haramyar-staff"); }
  private MockHttpServletRequestBuilder reviewer(MockHttpServletRequestBuilder r) { return as(r, REVIEWER, "haramyar-staff"); }

  private MockHttpServletResponse call(MockHttpServletRequestBuilder request, Object body) throws Exception {
    if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(request).andReturn().getResponse();
  }

  private JsonNode ok(MockHttpServletResponse response, int status) throws Exception {
    assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
    return response.getContentAsString().isEmpty() ? null : json.readTree(response.getContentAsString());
  }

  private Map<String, Object> content(String summary, String biography) {
    var content = new java.util.HashMap<String, Object>();
    content.put("fullName", "نمونه توسعه (Fixture)");
    content.put("contentLocale", "fa");
    content.put("shortDescription", summary);
    content.put("biography", biography);
    content.put("birth", Map.of("date", Map.of("year", 1340, "calendar", "SOLAR_HIJRI"), "place", "Fixture place"));
    content.put("seoTitle", "Fixture profile");
    return content;
  }

  private String publishedProfile(String slug) throws Exception {
    JsonNode draft = ok(call(editor(post("/editorial/profiles")), Map.of("slug", slug, "content", content("Synthetic summary", "Synthetic biography for tests."))), 201);
    String id = draft.path("id").asText();
    ok(call(editor(post("/editorial/profiles/" + id + "/sources")), Map.of("kind", "ARCHIVE", "title", "Fixture archive record", "url", "https://example.org/r/1")), 200);
    ok(call(editor(post("/editorial/profiles/" + id + "/submit")), Map.of()), 200);
    ok(call(reviewer(post("/editorial/profiles/" + id + "/publish")), Map.of()), 200);
    return id;
  }

  @Test
  void draftsAreInvisibleOnEveryPublicSurface() throws Exception {
    JsonNode draft = ok(call(editor(post("/editorial/profiles")), Map.of("slug", "fixture-draft", "content", content("Summary", "Bio"))), 201);
    assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
    assertThat(call(get("/public/profiles/fixture-draft"), null).getStatus()).isEqualTo(404);
    assertThat(ok(call(get("/public/profiles").param("q", "Fixture"), null), 200).path("total").asLong()).isZero();
    assertThat(ok(call(get("/public/sitemap"), null), 200).size()).isZero();
    assertThat(call(get("/public/profiles/../editorial/profiles"), null).getStatus()).isIn(400, 404);
  }

  @Test
  void editorialWorkflowWithFourEyesAndPublication() throws Exception {
    JsonNode draft = ok(call(editor(post("/editorial/profiles")), Map.of("slug", "fixture-profile", "content", content(null, null))), 201);
    String id = draft.path("id").asText();
    JsonNode notReady = ok(call(editor(post("/editorial/profiles/" + id + "/submit")), Map.of()), 422);
    assertThat(notReady.path("code").asText()).isEqualTo("EDITORIAL_NOT_READY_FOR_REVIEW");
    ok(call(editor(put("/editorial/profiles/" + id)), Map.of("expectedVersion", 0, "content", content("Synthetic summary", "Synthetic biography."))), 200);
    JsonNode stale = ok(call(editor(put("/editorial/profiles/" + id)), Map.of("expectedVersion", 0, "content", content("x", "y"))), 409);
    assertThat(stale.path("code").asText()).isEqualTo("REVISION_CONFLICT");
    ok(call(editor(post("/editorial/profiles/" + id + "/sources")), Map.of("kind", "INTERVIEW", "title", "Fixture interview")), 200);
    ok(call(editor(post("/editorial/profiles/" + id + "/submit")), Map.of()), 200);
    assertThat(call(get("/public/profiles/fixture-profile"), null).getStatus()).as("in review is not public").isEqualTo(404);

    JsonNode selfPublish = ok(call(as(post("/editorial/profiles/" + id + "/publish"), EDITOR, "haramyar-staff"), Map.of()), 403);
    assertThat(selfPublish.path("code").asText()).isEqualTo("EDITORIAL_FOUR_EYES_REQUIRED");
    JsonNode published = ok(call(reviewer(post("/editorial/profiles/" + id + "/publish")), Map.of()), 200);
    assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");

    JsonNode publicView = ok(call(get("/public/profiles/fixture-profile"), null), 200);
    assertThat(publicView.path("fullName").asText()).contains("Fixture");
    assertThat(publicView.toString()).doesNotContain(EDITOR.toString(), REVIEWER.toString(), "history", "submittedBy");
    assertThat(publicView.path("sources").size()).isEqualTo(1);
    assertThat(ok(call(get("/public/sitemap"), null), 200).get(0).path("slug").asText()).isEqualTo("fixture-profile");

    JsonNode frozen = ok(call(editor(put("/editorial/profiles/" + id)), Map.of("content", content("changed", "changed"))), 409);
    assertThat(frozen.path("code").asText()).isEqualTo("EDITORIAL_NOT_EDITABLE");

    ok(call(reviewer(post("/editorial/profiles/" + id + "/archive")), Map.of("note", "Re-verification")), 200);
    assertThat(call(get("/public/profiles/fixture-profile"), null).getStatus()).as("archived is unpublished").isEqualTo(404);
    assertThat(db.queryForObject("select count(*) from martyr_editorial_history where profile_id = ?::uuid", Integer.class, id)).isEqualTo(4);
  }

  @Test
  void customersCannotEnterTheEditorialWorkspace() throws Exception {
    JsonNode denied = ok(call(as(post("/editorial/profiles"), CUSTOMER, "haramyar-customer"), Map.of("slug", "intruder-profile", "content", content("x", "y"))), 403);
    assertThat(denied.path("code").asText()).isEqualTo("STAFF_AUTHENTICATION_REQUIRED");
    assertThat(call(get("/editorial/profiles"), null).getStatus()).isEqualTo(401);
    assertThat(db.queryForObject("select count(*) from martyr_profile", Integer.class)).isZero();
  }

  @Test
  void searchFindsPublishedProfilesAcrossArabicAndPersianSpelling() throws Exception {
    JsonNode draft = ok(call(editor(post("/editorial/profiles")), Map.of("slug", "search-fixture", "content", Map.of(
        "fullName", "علی نمونه (Fixture)", "contentLocale", "fa", "shortDescription", "Synthetic", "biography", "Synthetic biography."))), 201);
    String id = draft.path("id").asText();
    ok(call(editor(post("/editorial/profiles/" + id + "/sources")), Map.of("kind", "OTHER", "title", "Fixture source")), 200);
    ok(call(editor(post("/editorial/profiles/" + id + "/submit")), Map.of()), 200);
    ok(call(reviewer(post("/editorial/profiles/" + id + "/publish")), Map.of()), 200);
    assertThat(ok(call(get("/public/profiles").param("q", "علي"), null), 200).path("total").asLong()).isEqualTo(1);
    assertThat(ok(call(get("/public/profiles").param("q", "نمونه"), null), 200).path("items").get(0).path("slug").asText()).isEqualTo("search-fixture");
  }

  @Test
  void mediaIsValidatedStoredPrivatelyAndServedOnlyForPublishedProfiles() throws Exception {
    JsonNode draft = ok(call(editor(post("/editorial/profiles")), Map.of("slug", "media-fixture", "content", content("Synthetic", "Synthetic biography."))), 201);
    String id = draft.path("id").asText();
    MockMultipartFile disguisedSvg = new MockMultipartFile("file", "portrait.png", "image/png", "<svg onload='alert(1)'/>".getBytes());
    JsonNode rejected = ok(mvc.perform(editor(multipart("/editorial/profiles/" + id + "/media").file(disguisedSvg).param("usage", "PORTRAIT"))).andReturn().getResponse(), 400);
    assertThat(rejected.path("code").asText()).isEqualTo("EDITORIAL_INVALID_VALUE");

    MockMultipartFile image = new MockMultipartFile("file", "x.bin", "application/octet-stream", PNG);
    JsonNode withPortrait = ok(mvc.perform(editor(multipart("/editorial/profiles/" + id + "/media").file(image).param("usage", "PORTRAIT").param("caption", "Fixture caption"))).andReturn().getResponse(), 200);
    String mediaId = withPortrait.path("portrait").path("mediaId").asText();
    assertThat(db.queryForObject("select content_type from media_object where id = ?::uuid", String.class, mediaId)).isEqualTo("image/png");
    assertThat(call(get("/public/media/" + mediaId), null).getStatus()).as("draft media is private").isEqualTo(404);
    MockHttpServletResponse preview = call(editor(get("/editorial/media/" + mediaId)), null);
    assertThat(preview.getStatus()).isEqualTo(200);
    assertThat(preview.getContentAsByteArray()).isEqualTo(PNG);
    assertThat(preview.getHeader("Cache-Control")).contains("no-store");

    ok(call(editor(post("/editorial/profiles/" + id + "/sources")), Map.of("kind", "BOOK", "title", "Fixture book")), 200);
    ok(call(editor(post("/editorial/profiles/" + id + "/submit")), Map.of()), 200);
    ok(call(reviewer(post("/editorial/profiles/" + id + "/publish")), Map.of()), 200);
    MockHttpServletResponse served = call(get("/public/media/" + mediaId), null);
    assertThat(served.getStatus()).isEqualTo(200);
    assertThat(served.getContentType()).isEqualTo("image/png");
    assertThat(served.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(served.getContentAsByteArray()).isEqualTo(PNG);
    assertThat(call(get("/public/media/" + UUID.randomUUID()), null).getStatus()).isEqualTo(404);
  }

  @Test
  void historyIsAppendOnly() throws Exception {
    String id = publishedProfile("history-fixture");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> db.update("update martyr_editorial_history set note = 'x' where profile_id = ?::uuid", id))
        .hasMessageContaining("append-only");
  }
}
