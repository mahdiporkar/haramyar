package ir.haramyar.shell.occasion;

import io.hiveplatform.spring.HiveException;
import ir.haramyar.kit.security.CurrentActor;
import ir.haramyar.kit.security.HaramyarSecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Theme in effect (anonymous) and occasion administration. Administration requires the Hive business permission
 * {@code haramyar-app.occasions:manage} (checked by the BFF) and a staff account (checked here). It is a business
 * administrator capability, independent of Hive platform roles.
 */
@RestController
class OccasionController {
  record OccasionBody(@NotNull String key, @NotNull Occasion.Theme theme, Map<String, String> titles, Map<String, String> subtitles,
      @NotNull Instant startsAt, @NotNull Instant endsAt, int priority, Long expectedVersion) {}

  private final OccasionRepository occasions;
  private final HaramyarSecurityProperties security;
  private final Clock clock;

  OccasionController(OccasionRepository occasions, HaramyarSecurityProperties security, Clock clock) {
    this.occasions = occasions;
    this.security = security;
    this.clock = clock;
  }

  @GetMapping("/public/theme")
  ResponseEntity<Map<String, Object>> theme() {
    ActiveTheme active = ActiveTheme.resolve(occasions.scheduled(), clock.instant());
    var body = new LinkedHashMap<String, Object>();
    body.put("theme", active.theme().name().toLowerCase());
    active.occasion().ifPresent(o -> body.put("occasion", Map.of("key", o.key(), "titles", o.titles(), "subtitles", o.subtitles(), "endsAt", o.endsAt())));
    return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()).body(body);
  }

  @GetMapping("/admin/occasions")
  List<Map<String, Object>> list() {
    staff();
    return occasions.all().stream().map(OccasionController::view).toList();
  }

  @PostMapping("/admin/occasions")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  Map<String, Object> create(@Valid @RequestBody OccasionBody body) {
    staff();
    Occasion occasion = guard(() -> Occasion.draft(body.key(), body.theme(), body.titles(), body.subtitles(), body.startsAt(), body.endsAt(), body.priority()));
    occasions.add(occasion);
    return view(occasion);
  }

  @PutMapping("/admin/occasions/{id}")
  @Transactional
  Map<String, Object> revise(@PathVariable UUID id, @Valid @RequestBody OccasionBody body) {
    staff();
    Occasion occasion = load(id, body.expectedVersion());
    guard(() -> { occasion.revise(body.theme(), body.titles(), body.subtitles(), body.startsAt(), body.endsAt(), body.priority()); return occasion; });
    return save(occasion);
  }

  @PostMapping("/admin/occasions/{id}/schedule")
  @Transactional
  Map<String, Object> schedule(@PathVariable UUID id) {
    staff();
    Occasion occasion = load(id, null);
    guard(() -> { occasion.schedule(); return occasion; });
    return save(occasion);
  }

  @PostMapping("/admin/occasions/{id}/archive")
  @Transactional
  Map<String, Object> archive(@PathVariable UUID id) {
    staff();
    Occasion occasion = load(id, null);
    guard(() -> { occasion.archive(); return occasion; });
    return save(occasion);
  }

  private void staff() { CurrentActor.requireStaff(security.staffRole()); }

  private Occasion load(UUID id, Long expectedVersion) {
    Occasion occasion = occasions.find(id).orElseThrow(() -> HiveException.notFound("Unknown occasion"));
    if (expectedVersion != null && expectedVersion != occasion.version()) throw HiveException.stale();
    return occasion;
  }

  private Map<String, Object> save(Occasion occasion) {
    if (!occasions.update(occasion)) throw HiveException.stale();
    return view(occasions.find(occasion.id()).orElseThrow());
  }

  private static Occasion guard(java.util.function.Supplier<Occasion> action) {
    try {
      return action.get();
    } catch (IllegalArgumentException invalid) {
      throw HiveException.invalid(invalid.getMessage());
    } catch (IllegalStateException conflict) {
      throw new HiveException(HttpStatus.CONFLICT, "OCCASION_INVALID_TRANSITION", conflict.getMessage());
    }
  }

  static Map<String, Object> view(Occasion o) {
    var body = new LinkedHashMap<String, Object>();
    body.put("id", o.id());
    body.put("key", o.key());
    body.put("theme", o.theme());
    body.put("titles", o.titles());
    body.put("subtitles", o.subtitles());
    body.put("startsAt", o.startsAt());
    body.put("endsAt", o.endsAt());
    body.put("priority", o.priority());
    body.put("status", o.status());
    body.put("version", o.version());
    return body;
  }
}
