package ir.haramyar.kit.messaging;

import java.time.Duration;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Broker publication of the outbox. Requires {@code spring.rabbitmq.publisher-confirm-type=simple}; disabled with
 * {@code haramyar.messaging.publisher.enabled=false} (for example in services that only consume).
 */
@AutoConfiguration(after = {RabbitAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean({JdbcTemplate.class, RabbitTemplate.class})
@ConditionalOnProperty(name = "haramyar.messaging.publisher.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class PublishingAutoConfiguration {
  @Bean Declarables haramyarExchanges() { return new Declarables(RabbitTopology.exchanges()); }

  @Bean MessageSink rabbitMessageSink(RabbitTemplate rabbit) { return new RabbitMessageSink(rabbit, 10_000); }

  @Bean OutboxPublisher outboxPublisher(JdbcTemplate db, MessageSink sink,
      @Value("${haramyar.messaging.publisher.batch-size:50}") int batchSize,
      @Value("${haramyar.messaging.publisher.max-attempts:10}") int maxAttempts) {
    return new OutboxPublisher(db, sink, batchSize, maxAttempts, Duration.ofSeconds(60));
  }

  @Bean OutboxPoller outboxPoller(OutboxPublisher publisher) { return new OutboxPoller(publisher); }

  @Bean HealthIndicator outboxHealthIndicator(OutboxPublisher publisher) {
    return () -> Health.up().withDetails(Map.copyOf(publisher.statistics())).build();
  }

  /** Scheduling wrapper so the publisher itself stays a plain, directly testable class. */
  public static class OutboxPoller {
    private final OutboxPublisher publisher;

    OutboxPoller(OutboxPublisher publisher) { this.publisher = publisher; }

    @Scheduled(fixedDelayString = "${haramyar.messaging.publisher.interval-ms:1000}", initialDelayString = "${haramyar.messaging.publisher.initial-delay-ms:3000}")
    public void poll() {
      try {
        while (publisher.publishBatch() > 0) { /* drain while records are due */ }
      } catch (RuntimeException failure) {
        LoggerFactory.getLogger(OutboxPoller.class).warn("Outbox poll failed: {}", failure.toString());
      }
    }
  }
}
