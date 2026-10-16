package ir.haramyar.kit.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side idempotency. {@link #firstDelivery} records (consumer, event id) in the consumer's own transaction, so
 * the business change and the inbox record commit together; a redelivered event finds the record and is skipped.
 */
public class Inbox {
  private final JdbcTemplate db;

  public Inbox(JdbcTemplate db) { this.db = db; }

  /** @return true if this consumer has not processed the event before (the caller must then perform the work) */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean firstDelivery(String consumer, UUID eventId, String eventType) {
    return db.update("insert into inbox_message(consumer, event_id, event_type) values (?, ?, ?) on conflict do nothing", consumer, eventId, eventType) == 1;
  }
}
