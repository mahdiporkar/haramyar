package ir.haramyar.rozeh.infrastructure.adapter;

import ir.haramyar.kit.messaging.IntegrationEvent;
import ir.haramyar.kit.messaging.OutboxWriter;
import ir.haramyar.rozeh.application.port.IntegrationEvents;

/** Records integration events in the transactional outbox of the booking database. */
public class OutboxIntegrationEvents implements IntegrationEvents {
  private final OutboxWriter outbox;

  public OutboxIntegrationEvents(OutboxWriter outbox) { this.outbox = outbox; }

  @Override
  public void record(IntegrationEvent event) { outbox.append(event); }
}
