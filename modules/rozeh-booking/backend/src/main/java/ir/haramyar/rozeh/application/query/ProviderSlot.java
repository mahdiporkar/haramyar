package ir.haramyar.rozeh.application.query;

import java.time.Instant;
import java.util.UUID;

/** A confirmed commitment of a provider (no host details). */
public record ProviderSlot(UUID bookingId, String trackingCode, Instant startsAt, Instant endsAt) {}
