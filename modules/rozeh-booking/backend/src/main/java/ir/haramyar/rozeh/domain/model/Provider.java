package ir.haramyar.rozeh.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A ceremony provider that coordinators may assign to accepted bookings. Provider self-service and profiles are not
 * part of the first release; this aggregate holds only what assignment needs.
 */
public final class Provider {
  private final ProviderId id;
  private String displayName;
  private boolean active;
  private final Instant registeredAt;
  private final long version;

  private Provider(ProviderId id, String displayName, boolean active, Instant registeredAt, long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.displayName = Text.required(displayName, "Provider name", 2, 120);
    this.active = active;
    this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
    this.version = version;
  }

  public static Provider register(ProviderId id, String displayName, Instant now) {
    return new Provider(id, displayName, true, now, 0);
  }

  public static Provider reconstitute(ProviderId id, String displayName, boolean active, Instant registeredAt, long version) {
    return new Provider(id, displayName, active, registeredAt, version);
  }

  public void rename(String newName) { displayName = Text.required(newName, "Provider name", 2, 120); }
  public void deactivate() { active = false; }
  public void activate() { active = true; }

  public void ensureAssignable() {
    if (!active) throw new BookingRuleViolation(BookingRuleViolation.Code.PROVIDER_INACTIVE, "The provider is not active");
  }

  public ProviderId id() { return id; }
  public String displayName() { return displayName; }
  public boolean active() { return active; }
  public Instant registeredAt() { return registeredAt; }
  public long version() { return version; }
}
