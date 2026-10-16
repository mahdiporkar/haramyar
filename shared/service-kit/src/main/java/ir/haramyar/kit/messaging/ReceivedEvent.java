package ir.haramyar.kit.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** An integration event as delivered to a consumer (the envelope written by {@link OutboxPublisher}). */
public record ReceivedEvent(UUID eventId, String type, String aggregateType, String aggregateId, Instant occurredAt, JsonNode payload) {}
