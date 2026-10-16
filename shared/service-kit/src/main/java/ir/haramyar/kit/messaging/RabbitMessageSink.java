package ir.haramyar.kit.messaging;

import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Publishes to {@value RabbitTopology#EXCHANGE} with persistent delivery and waits for the broker confirm. */
public class RabbitMessageSink implements MessageSink {
  private final RabbitTemplate rabbit;
  private final long confirmTimeoutMs;

  /** @param rabbit a template whose connection factory uses {@code SIMPLE} publisher confirms */
  public RabbitMessageSink(RabbitTemplate rabbit, long confirmTimeoutMs) {
    this.rabbit = rabbit;
    this.confirmTimeoutMs = confirmTimeoutMs;
  }

  @Override
  public void publish(String eventId, String eventType, String body) {
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(eventId);
    properties.setType(eventType);
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setContentEncoding(StandardCharsets.UTF_8.name());
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    Message message = new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    rabbit.invoke(operations -> {
      operations.send(RabbitTopology.EXCHANGE, eventType, message);
      operations.waitForConfirmsOrDie(confirmTimeoutMs);
      return null;
    });
  }
}
