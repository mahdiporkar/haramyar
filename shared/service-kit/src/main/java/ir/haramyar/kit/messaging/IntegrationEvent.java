package ir.haramyar.kit.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A versioned integration contract published to other bounded contexts. Event types follow
 * {@code haramyar.<context>.<fact>.v<major>}; payloads carry identifiers and facts, not personal details.
 */
public record IntegrationEvent(UUID eventId, String type, String aggregateType, String aggregateId, Instant occurredAt, Map<String, Object> payload) {
  private static final Pattern TYPE = Pattern.compile("haramyar\\.[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*\\.v[1-9][0-9]*");

  public IntegrationEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (type == null || !TYPE.matcher(type).matches()) throw new IllegalArgumentException("Invalid integration event type: " + type);
    if (aggregateType == null || aggregateType.isBlank() || aggregateId == null || aggregateId.isBlank()) throw new IllegalArgumentException("Aggregate reference required");
    payload = Map.copyOf(payload == null ? Map.of() : payload);
  }
}
