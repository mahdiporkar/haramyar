package ir.haramyar.shell.notification;

import ir.haramyar.kit.messaging.PermanentProcessingException;
import ir.haramyar.kit.messaging.ReceivedEvent;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Turns booking integration events into in-app notifications for the host. Runs inside the consumer's transaction
 * together with the inbox record, so a redelivered event never creates a second notification. Texts are not stored:
 * the client renders the notification kind in the user's language (fa, ar, en).
 */
public class BookingNotificationHandler implements Consumer<ReceivedEvent> {
  static final Map<String, String> KINDS = Map.of(
      "haramyar.booking.requested.v1", "BOOKING_REQUESTED",
      "haramyar.booking.accepted.v1", "BOOKING_ACCEPTED",
      "haramyar.booking.rejected.v1", "BOOKING_REJECTED",
      "haramyar.booking.confirmed.v1", "BOOKING_CONFIRMED",
      "haramyar.booking.cancelled.v1", "BOOKING_CANCELLED",
      "haramyar.booking.completed.v1", "BOOKING_COMPLETED");

  private final JdbcTemplate db;

  public BookingNotificationHandler(JdbcTemplate db) { this.db = db; }

  @Override
  public void accept(ReceivedEvent event) {
    String kind = KINDS.get(event.type());
    if (kind == null) throw new PermanentProcessingException("Unsupported event contract " + event.type());
    UUID host;
    UUID booking;
    try {
      host = UUID.fromString(event.payload().path("hostId").asText());
      booking = UUID.fromString(event.payload().path("bookingId").asText());
    } catch (IllegalArgumentException malformed) {
      throw new PermanentProcessingException("Booking event without valid hostId/bookingId", malformed);
    }
    String trackingCode = event.payload().path("trackingCode").asText(null);
    db.update("insert into notification(id, hive_user_id, kind, subject_id, reference, event_id, occurred_at) values (?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(), host, kind, booking, trackingCode, event.eventId(), java.sql.Timestamp.from(event.occurredAt()));
  }
}
