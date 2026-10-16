package ir.haramyar.kit.messaging;

/** Durable delivery of one serialized integration event; returns only after the broker has confirmed it. */
public interface MessageSink {
  void publish(String eventId, String eventType, String body) throws Exception;
}
