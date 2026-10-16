package ir.haramyar.kit.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * At-least-once consumer with exactly-once effects. For each delivery:
 * <ol>
 *   <li>parse the envelope (failures are permanent);</li>
 *   <li>in one database transaction, record the event in the inbox and run the handler only on first delivery;</li>
 *   <li>after commit, acknowledge. A crash between commit and ack causes a redelivery that the inbox skips.</li>
 * </ol>
 * Transient failures are republished to the next retry stage (exponential, bounded by
 * {@link RabbitTopology#RETRY_DELAYS_MS}); permanent failures and exhausted retries go to the dead-letter queue with
 * diagnostics. The original delivery is acknowledged only after the republish was confirmed; if even that fails the
 * delivery is returned to the broker after a pause instead of being lost.
 */
public class ReliableEventConsumer implements ChannelAwareMessageListener {
  public static final String ATTEMPT_HEADER = "x-haramyar-attempt";
  public static final String ERROR_HEADER = "x-haramyar-error";
  private static final Logger log = LoggerFactory.getLogger(ReliableEventConsumer.class);

  private final String consumer;
  private final String queue;
  private final Inbox inbox;
  private final TransactionTemplate transactions;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;
  private final Consumer<ReceivedEvent> handler;
  private final int maxRetries;

  public ReliableEventConsumer(String consumer, String queue, Inbox inbox, TransactionTemplate transactions, RabbitTemplate rabbit, ObjectMapper json, Consumer<ReceivedEvent> handler) {
    this(consumer, queue, inbox, transactions, rabbit, json, handler, RabbitTopology.RETRY_DELAYS_MS.size());
  }

  /** @param maxRetries number of retry stages declared for {@code queue} (see {@link RabbitTopology#consumer}) */
  public ReliableEventConsumer(String consumer, String queue, Inbox inbox, TransactionTemplate transactions, RabbitTemplate rabbit, ObjectMapper json, Consumer<ReceivedEvent> handler, int maxRetries) {
    this.consumer = consumer;
    this.queue = queue;
    this.inbox = inbox;
    this.transactions = transactions;
    this.rabbit = rabbit;
    this.json = json;
    this.handler = handler;
    this.maxRetries = maxRetries;
  }

  @Override
  public void onMessage(Message message, Channel channel) throws Exception {
    long tag = message.getMessageProperties().getDeliveryTag();
    ReceivedEvent event;
    try {
      event = parse(message);
    } catch (RuntimeException malformed) {
      deadLetter(message, channel, tag, "Malformed envelope: " + malformed.getMessage());
      return;
    }
    try {
      transactions.executeWithoutResult(status -> {
        if (inbox.firstDelivery(consumer, event.eventId(), event.type())) handler.accept(event);
        else log.info("Event {} ({}) already processed by {}; skipped", event.eventId(), event.type(), consumer);
      });
      channel.basicAck(tag, false);
    } catch (PermanentProcessingException permanent) {
      deadLetter(message, channel, tag, permanent.getMessage());
    } catch (RuntimeException transientFailure) {
      int attempt = attempt(message);
      if (attempt >= maxRetries) {
        deadLetter(message, channel, tag, "Retries exhausted: " + transientFailure);
      } else {
        republish(message, channel, tag, RabbitTopology.retryQueue(queue, attempt + 1), attempt + 1, transientFailure.toString());
        log.warn("Event {} ({}) failed in {} (attempt {}); scheduled retry stage {}", event.eventId(), event.type(), consumer, attempt + 1, attempt + 1);
      }
    }
  }

  ReceivedEvent parse(Message message) {
    try {
      JsonNode root = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
      return new ReceivedEvent(UUID.fromString(root.path("eventId").asText()), required(root, "type"), required(root, "aggregateType"), required(root, "aggregateId"),
          Instant.parse(required(root, "occurredAt")), root.path("payload"));
    } catch (java.io.IOException | IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  private static String required(JsonNode root, String field) {
    String value = root.path(field).asText("");
    if (value.isBlank()) throw new IllegalArgumentException("Missing " + field);
    return value;
  }

  private static int attempt(Message message) {
    Object value = message.getMessageProperties().getHeaders().get(ATTEMPT_HEADER);
    return value instanceof Number number ? number.intValue() : 0;
  }

  private void deadLetter(Message message, Channel channel, long tag, String reason) throws Exception {
    log.error("Event {} dead-lettered by {}: {}", message.getMessageProperties().getMessageId(), consumer, reason);
    republish(message, channel, tag, RabbitTopology.deadLetterQueue(queue), attempt(message), reason);
  }

  private void republish(Message original, Channel channel, long tag, String targetQueue, int attempt, String reason) throws Exception {
    MessageProperties properties = original.getMessageProperties();
    properties.setHeader(ATTEMPT_HEADER, attempt);
    String diagnostic = reason.replaceAll("\\s+", " ");
    properties.setHeader(ERROR_HEADER, diagnostic.length() > 500 ? diagnostic.substring(0, 500) : diagnostic);
    properties.setHeader("x-haramyar-original-routing-key", properties.getReceivedRoutingKey());
    try {
      rabbit.invoke(operations -> {
        operations.send("", targetQueue, new Message(original.getBody(), properties));
        operations.waitForConfirmsOrDie(10_000);
        return null;
      });
      channel.basicAck(tag, false);
    } catch (RuntimeException brokerFailure) {
      log.error("Could not move event {} to {}; returning it to the broker", properties.getMessageId(), targetQueue, brokerFailure);
      Thread.sleep(1_000);
      channel.basicNack(tag, false, true);
    }
  }
}
