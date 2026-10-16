package ir.haramyar.rozeh.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A ceremony provider (reciter) registered by coordinators. */
public record ProviderId(UUID value) {
  public ProviderId { Objects.requireNonNull(value, "providerId"); }

  public static ProviderId newId() { return new ProviderId(UUID.randomUUID()); }

  @Override public String toString() { return value.toString(); }
}
