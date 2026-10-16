package ir.haramyar.kit.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends integration events to the transactional outbox. {@link Propagation#MANDATORY} guarantees the record is
 * written in the same database transaction as the aggregate change that produced it: both commit or neither does.
 */
public class OutboxWriter {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public OutboxWriter(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void append(IntegrationEvent event) {
    String payload;
    try {
      payload = json.writeValueAsString(event.payload());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Integration event payload is not serializable", e);
    }
    db.update("insert into outbox_event(id, event_type, aggregate_type, aggregate_id, occurred_at, payload) values (?, ?, ?, ?, ?, cast(? as jsonb))",
        event.eventId(), event.type(), event.aggregateType(), event.aggregateId(), Timestamp.from(event.occurredAt()), payload);
  }
}
