package ir.haramyar.rozeh.application.query;

import ir.haramyar.rozeh.domain.model.BookingStatus;
import java.time.Instant;
import java.util.UUID;

/** List row; deliberately without phone number or street address. */
public record BookingSummary(UUID id, String trackingCode, BookingStatus status, String hostName, String city, String area, String category, String audience,
    Instant startsAt, Instant endsAt, String timeZone, UUID providerId, String providerName, Instant requestedAt, Instant updatedAt) {}
