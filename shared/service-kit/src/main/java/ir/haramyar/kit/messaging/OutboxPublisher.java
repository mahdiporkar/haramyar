package ir.haramyar.kit.messaging;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Polling publisher for the transactional outbox. Workers claim due records with {@code FOR UPDATE SKIP LOCKED} and a
 * lease, so several instances never publish the same record concurrently and a crashed worker's claims expire. Each
 * record is marked published only after the broker confirmed it; failures back off exponentially and are parked as
 * {@code FAILED} after {@code maxAttempts}, which keeps recovery bounded and observable.
 */
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate db;
  private final MessageSink sink;
  private final int batchSize;
  private final int maxAttempts;
  private final Duration lease;
  private final String workerId = "outbox-" + UUID.randomUUID();

  public OutboxPublisher(JdbcTemplate db, MessageSink sink, int batchSize, int maxAttempts, Duration lease) {
    this.db = db;
    this.sink = sink;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.lease = lease;
  }

  record Claimed(UUID id, String type, String body, int attempts) {}

  /** Publishes one batch; returns the number of records confirmed by the broker. */
  public int publishBatch() {
    List<Claimed> claimed = db.query("""
        update outbox_event set status = 'IN_FLIGHT', claimed_by = ?, claimed_until = now() + make_interval(secs => ?), attempts = attempts + 1
        where id in (
          select id from outbox_event
          where (status = 'PENDING' and next_attempt_at <= now()) or (status = 'IN_FLIGHT' and claimed_until < now())
          order by created_at
          limit ?
          for update skip locked)
        returning id, event_type, attempts,
          json_build_object('eventId', id, 'type', event_type, 'aggregateType', aggregate_type, 'aggregateId', aggregate_id,
                            'occurredAt', to_char(occurred_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), 'payload', payload)::text as body
        """, (row, n) -> new Claimed(row.getObject("id", UUID.class), row.getString("event_type"), row.getString("body"), row.getInt("attempts")),
        workerId, lease.toSeconds(), batchSize);
    int published = 0;
    for (Claimed record : claimed) {
      try {
        sink.publish(record.id().toString(), record.type(), record.body());
        db.update("update outbox_event set status = 'PUBLISHED', published_at = now(), claimed_by = null, claimed_until = null, last_error = null where id = ? and claimed_by = ?", record.id(), workerId);
        published++;
      } catch (Exception failure) {
        String error = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        if (record.attempts() >= maxAttempts) {
          db.update("update outbox_event set status = 'FAILED', claimed_by = null, claimed_until = null, last_error = left(?, 1000) where id = ? and claimed_by = ?", error, record.id(), workerId);
          log.error("Outbox event {} ({}) parked as FAILED after {} attempts", record.id(), record.type(), record.attempts());
        } else {
          long delaySeconds = Math.min(300, (long) Math.pow(2, record.attempts()));
          db.update("update outbox_event set status = 'PENDING', claimed_by = null, claimed_until = null, next_attempt_at = now() + make_interval(secs => ?), last_error = left(?, 1000) where id = ? and claimed_by = ?",
              delaySeconds, error, record.id(), workerId);
          log.warn("Outbox event {} ({}) publication failed (attempt {}), retry in {}s", record.id(), record.type(), record.attempts(), delaySeconds);
        }
      }
    }
    return published;
  }

  /** Counts by status for health reporting and operations. */
  public Map<String, Long> statistics() {
    var counts = new java.util.TreeMap<String, Long>();
    db.query("select status, count(*) from outbox_event group by status", row -> { counts.put(row.getString(1), row.getLong(2)); });
    return counts;
  }

  /** Returns parked records to the queue after the cause was fixed (operator action). */
  public int requeueFailed() {
    return db.update("update outbox_event set status = 'PENDING', attempts = 0, next_attempt_at = now(), last_error = null where status = 'FAILED'");
  }
}
