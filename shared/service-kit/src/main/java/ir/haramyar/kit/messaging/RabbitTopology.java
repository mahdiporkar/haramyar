package ir.haramyar.kit.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Broker topology shared by publishers and consumers. The same declarations are loaded at broker start from
 * {@code deployment/rabbitmq/definitions.json}; arguments must stay identical in both places.
 * <ul>
 *   <li>{@value #EXCHANGE}: durable topic exchange, routing key = event type;</li>
 *   <li>{@value #UNROUTED}: alternate exchange and queue that park events no consumer queue is bound for (no silent loss);</li>
 *   <li>per consumer queue {@code q}: {@code q.retry.1..n} delay queues whose fixed TTL dead-letters back to {@code q},
 *       and {@code q.dlq} for permanent failures and exhausted retries.</li>
 * </ul>
 */
public final class RabbitTopology {
  public static final String EXCHANGE = "haramyar.events";
  public static final String UNROUTED = "haramyar.events.unrouted";
  /** Delays of the retry stages; their count is the maximum number of retries. */
  public static final List<Integer> RETRY_DELAYS_MS = List.of(1_000, 5_000, 25_000);

  private RabbitTopology() {}

  public static List<Declarable> exchanges() {
    TopicExchange events = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).alternate(UNROUTED).build();
    FanoutExchange unrouted = ExchangeBuilder.fanoutExchange(UNROUTED).durable(true).build();
    Queue parked = QueueBuilder.durable(UNROUTED).build();
    return List.of(events, unrouted, parked, BindingBuilder.bind(parked).to(unrouted));
  }

  public static List<Declarable> consumer(String queue, List<String> routingKeys) {
    return consumer(queue, routingKeys, RETRY_DELAYS_MS);
  }

  public static List<Declarable> consumer(String queue, List<String> routingKeys, List<Integer> retryDelaysMs) {
    var declarables = new ArrayList<Declarable>();
    Queue main = QueueBuilder.durable(queue).build();
    declarables.add(main);
    TopicExchange events = new TopicExchange(EXCHANGE, true, false, Map.of("alternate-exchange", UNROUTED));
    for (String key : routingKeys) declarables.add(new Binding(queue, Binding.DestinationType.QUEUE, EXCHANGE, key, null));
    declarables.add(events);
    for (int stage = 1; stage <= retryDelaysMs.size(); stage++) {
      declarables.add(QueueBuilder.durable(retryQueue(queue, stage)).ttl(retryDelaysMs.get(stage - 1))
          .deadLetterExchange("").deadLetterRoutingKey(queue).build());
    }
    declarables.add(QueueBuilder.durable(deadLetterQueue(queue)).build());
    return declarables;
  }

  public static String retryQueue(String queue, int stage) { return queue + ".retry." + stage; }
  public static String deadLetterQueue(String queue) { return queue + ".dlq"; }
}
