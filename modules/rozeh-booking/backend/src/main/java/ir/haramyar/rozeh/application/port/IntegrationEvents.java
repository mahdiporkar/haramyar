package ir.haramyar.rozeh.application.port;

import ir.haramyar.kit.messaging.IntegrationEvent;

/** Records integration events in the caller's transaction (transactional outbox). */
public interface IntegrationEvents {
  void record(IntegrationEvent event);
}
