package ir.haramyar.rozeh.domain.model;

import java.util.Objects;
import java.util.UUID;

/** The canonical Hive user who hosts the ceremony. Never a phone number. */
public record HostId(UUID value) {
  public HostId { Objects.requireNonNull(value, "hostId"); }

  @Override public String toString() { return value.toString(); }
}
