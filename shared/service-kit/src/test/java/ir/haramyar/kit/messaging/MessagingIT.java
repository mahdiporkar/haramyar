package ir.haramyar.kit.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Outbox, broker delivery, inbox deduplication, retry and dead-lettering against real PostgreSQL and RabbitMQ. */
@Testcontainers
class MessagingIT {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
  @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1.4-management-alpine");

  static DataSource dataSource;
  static JdbcTemplate db;
  static TransactionTemplate transactions;
  static AnnotationConfigApplicationContext spring;
  static CachingConnectionFactory connections;
  static RabbitTemplate rabbit;
  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  final List<SimpleMessageListenerContainer> listeners = new ArrayList<>();

  @Configuration
  @EnableTransactionManagement
  static class Config {
    @Bean DataSource dataSource() { return dataSource; }
    @Bean DataSourceTransactionManager transactionManager() { return new DataSourceTransactionManager(dataSource); }
    @Bean JdbcTemplate jdbcTemplate() { return db; }
    @Bean OutboxWriter outboxWriter() { return new OutboxWriter(db, JSON); }
    @Bean Inbox inbox() { return new Inbox(db); }
  }

  @BeforeAll
  static void start() {
    dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/kit").load().migrate();
    db = new JdbcTemplate(dataSource);
    db.execute("create table processed(event_id uuid primary key, consumer text not null)");
    spring = new AnnotationConfigApplicationContext(Config.class);
    transactions = new TransactionTemplate(spring.getBean(DataSourceTransactionManager.class));
    connections = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
    connections.setUsername(RABBIT.getAdminUsername());
    connections.setPassword(RABBIT.getAdminPassword());
    connections.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
    rabbit = new RabbitTemplate(connections);
    RabbitAdmin admin = new RabbitAdmin(connections);
    RabbitTopology.exchanges().forEach(d -> declare(admin, d));
  }

  static void declare(RabbitAdmin admin, org.springframework.amqp.core.Declarable declarable) {
    if (declarable instanceof org.springframework.amqp.core.Exchange exchange) admin.declareExchange(exchange);
    else if (declarable instanceof org.springframework.amqp.core.Queue queue) admin.declareQueue(queue);
    else if (declarable instanceof org.springframework.amqp.core.Binding binding) admin.declareBinding(binding);
  }

  @BeforeEach
  void clean() {
    db.update("delete from outbox_event");
    db.update("delete from inbox_message");
    db.update("delete from processed");
  }

  @AfterEach
  void stopListeners() { listeners.forEach(SimpleMessageListenerContainer::stop); }

  private static IntegrationEvent event(String type) {
    return new IntegrationEvent(UUID.randomUUID(), type, "Test", UUID.randomUUID().toString(), Instant.now(), Map.of("value", 1));
  }

  @Test
  void outboxAppendRequiresTheCallersTransaction() {
    OutboxWriter writer = spring.getBean(OutboxWriter.class);
    assertThatThrownBy(() -> writer.append(event("haramyar.test.created.v1"))).isInstanceOf(IllegalTransactionStateException.class);
    transactions.executeWithoutResult(s -> writer.append(event("haramyar.test.created.v1")));
    assertThat(db.queryForObject("select count(*) from outbox_event", Integer.class)).isEqualTo(1);
  }

  @Test
  void outboxRollsBackWithTheBusinessTransaction() {
    OutboxWriter writer = spring.getBean(OutboxWriter.class);
    assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
      writer.append(event("haramyar.test.created.v1"));
      throw new IllegalStateException("business change failed");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(db.queryForObject("select count(*) from outbox_event", Integer.class)).isZero();
  }

  @Test
  void concurrentWorkersPublishEveryRecordExactlyOnce() throws Exception {
    OutboxWriter writer = spring.getBean(OutboxWriter.class);
    transactions.executeWithoutResult(s -> { for (int i = 0; i < 300; i++) writer.append(event("haramyar.test.created.v1")); });
    Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
    MessageSink counting = (id, type, body) -> deliveries.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
    var pool = Executors.newFixedThreadPool(4);
    var start = new CountDownLatch(1);
    var futures = new ArrayList<java.util.concurrent.Future<?>>();
    for (int w = 0; w < 4; w++) {
      OutboxPublisher worker = new OutboxPublisher(db, counting, 20, 5, Duration.ofSeconds(60));
      futures.add(pool.submit(() -> { start.await(); while (worker.publishBatch() > 0) { } return null; }));
    }
    start.countDown();
    for (var f : futures) f.get();
    pool.shutdown();
    assertThat(deliveries).hasSize(300);
    assertThat(deliveries.values()).allMatch(count -> count.get() == 1);
    assertThat(db.queryForObject("select count(*) from outbox_event where status = 'PUBLISHED'", Integer.class)).isEqualTo(300);
  }

  @Test
  void failedPublicationBacksOffAndIsParkedAfterMaxAttempts() {
    OutboxWriter writer = spring.getBean(OutboxWriter.class);
    transactions.executeWithoutResult(s -> writer.append(event("haramyar.test.created.v1")));
    MessageSink broken = (id, type, body) -> { throw new java.io.IOException("broker unavailable"); };
    OutboxPublisher publisher = new OutboxPublisher(db, broken, 10, 2, Duration.ofSeconds(60));
    assertThat(publisher.publishBatch()).isZero();
    assertThat(db.queryForObject("select status from outbox_event", String.class)).isEqualTo("PENDING");
    assertThat(db.queryForObject("select next_attempt_at > now() from outbox_event", Boolean.class)).isTrue();
    assertThat(publisher.publishBatch()).as("not due yet").isZero();
    assertThat(db.queryForObject("select attempts from outbox_event", Integer.class)).isEqualTo(1);
    db.update("update outbox_event set next_attempt_at = now()");
    publisher.publishBatch();
    assertThat(db.queryForObject("select status from outbox_event", String.class)).isEqualTo("FAILED");
    assertThat(db.queryForObject("select last_error from outbox_event", String.class)).contains("broker unavailable");
    assertThat(publisher.statistics()).containsEntry("FAILED", 1L);
    assertThat(publisher.requeueFailed()).isEqualTo(1);
    assertThat(new OutboxPublisher(db, (id, type, body) -> { }, 10, 2, Duration.ofSeconds(60)).publishBatch()).isEqualTo(1);
  }

  @Test
  void expiredClaimsOfCrashedWorkersAreRecovered() {
    OutboxWriter writer = spring.getBean(OutboxWriter.class);
    transactions.executeWithoutResult(s -> writer.append(event("haramyar.test.created.v1")));
    db.update("update outbox_event set status = 'IN_FLIGHT', claimed_by = 'crashed', claimed_until = now() - interval '1 second', attempts = 1");
    assertThat(new OutboxPublisher(db, (id, type, body) -> { }, 10, 5, Duration.ofSeconds(60)).publishBatch()).isEqualTo(1);
    assertThat(db.queryForObject("select status from outbox_event", String.class)).isEqualTo("PUBLISHED");
  }

  private SimpleMessageListenerContainer listen(String queue, ReliableEventConsumer consumer) {
    var container = new SimpleMessageListenerContainer(connections);
    container.setQueueNames(queue);
    container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    container.setMessageListener(consumer);
    container.start();
    listeners.add(container);
    return container;
  }

  private void declareConsumer(String queue, String key, List<Integer> delays) {
    RabbitAdmin admin = new RabbitAdmin(connections);
    new Declarables(RabbitTopology.consumer(queue, List.of(key), delays)).getDeclarables().forEach(d -> declare(admin, d));
  }

  private void record(String consumer, ReceivedEvent event) {
    db.update("insert into processed(event_id, consumer) values (?, ?)", event.eventId(), consumer);
  }

  @Test
  void brokerDeliveryWithDuplicatesHasExactlyOnceEffect() throws Exception {
    String queue = "test.dedupe";
    declareConsumer(queue, "haramyar.dedupe.#", List.of(100));
    AtomicInteger handled = new AtomicInteger();
    listen(queue, new ReliableEventConsumer("dedupe", queue, spring.getBean(Inbox.class), transactions, rabbit, JSON,
        e -> { handled.incrementAndGet(); record("dedupe", e); }, 1));
    OutboxWriter writer = spring.getBean(OutboxWriter.class);
    IntegrationEvent event = event("haramyar.dedupe.created.v1");
    transactions.executeWithoutResult(s -> writer.append(event));
    OutboxPublisher publisher = new OutboxPublisher(db, new RabbitMessageSink(rabbit, 5000), 10, 5, Duration.ofSeconds(60));
    assertThat(publisher.publishBatch()).isEqualTo(1);
    // Simulate a redelivery of the same event (at-least-once delivery).
    db.update("update outbox_event set status = 'PENDING', next_attempt_at = now()");
    assertThat(publisher.publishBatch()).isEqualTo(1);
    await().atMost(Duration.ofSeconds(10)).until(() -> db.queryForObject("select count(*) from inbox_message", Integer.class) == 1
        && depth(queue) == 0);
    Thread.sleep(500);
    assertThat(handled.get()).isEqualTo(1);
    assertThat(db.queryForObject("select count(*) from processed where event_id = ?", Integer.class, event.eventId())).isEqualTo(1);
  }

  @Test
  void transientFailuresAreRetriedWithBackoffThenSucceed() {
    String queue = "test.retry";
    declareConsumer(queue, "haramyar.retry.#", List.of(200, 400, 800));
    AtomicInteger calls = new AtomicInteger();
    listen(queue, new ReliableEventConsumer("retry", queue, spring.getBean(Inbox.class), transactions, rabbit, JSON, e -> {
      if (calls.incrementAndGet() < 3) throw new IllegalStateException("downstream temporarily unavailable");
      record("retry", e);
    }, 3));
    IntegrationEvent event = event("haramyar.retry.created.v1");
    publishDirect(event);
    await().atMost(Duration.ofSeconds(15)).until(() -> db.queryForObject("select count(*) from processed", Integer.class) == 1);
    assertThat(calls.get()).isEqualTo(3);
    assertThat(depth(RabbitTopology.deadLetterQueue(queue))).isZero();
    // Failed attempts rolled back their inbox rows; only the successful one remains.
    assertThat(db.queryForObject("select count(*) from inbox_message where consumer = 'retry'", Integer.class)).isEqualTo(1);
  }

  @Test
  void exhaustedAndPermanentFailuresAreDeadLetteredWithDiagnostics() {
    String queue = "test.dlq";
    declareConsumer(queue, "haramyar.dlq.#", List.of(100, 200));
    listen(queue, new ReliableEventConsumer("dlq", queue, spring.getBean(Inbox.class), transactions, rabbit, JSON, e -> {
      if (e.type().contains("permanent")) throw new PermanentProcessingException("unsupported contract");
      throw new IllegalStateException("always failing");
    }, 2));
    publishDirect(event("haramyar.dlq.permanent.v1"));
    publishDirect(event("haramyar.dlq.transient.v1"));
    rabbit.convertAndSend(RabbitTopology.EXCHANGE, "haramyar.dlq.garbage.v1", "{not json");
    String dlq = RabbitTopology.deadLetterQueue(queue);
    await().atMost(Duration.ofSeconds(15)).until(() -> depth(dlq) == 3);
    Set<String> reasons = new java.util.HashSet<>();
    for (int i = 0; i < 3; i++) {
      Message dead = rabbit.receive(dlq, 2000);
      reasons.add(String.valueOf(dead.getMessageProperties().getHeaders().get(ReliableEventConsumer.ERROR_HEADER)).replaceAll(":.*", ""));
    }
    assertThat(reasons).containsExactlyInAnyOrder("unsupported contract", "Retries exhausted", "Malformed envelope");
    assertThat(db.queryForObject("select count(*) from processed", Integer.class)).isZero();
    assertThat(db.queryForObject("select count(*) from inbox_message where consumer = 'dlq'", Integer.class)).isZero();
  }

  @Test
  void eventsWithoutBoundConsumerAreParkedNotLost() throws Exception {
    rabbit.execute(c -> c.queuePurge(RabbitTopology.UNROUTED));
    new RabbitMessageSink(rabbit, 5000).publish(UUID.randomUUID().toString(), "haramyar.nobody.listens.v1", "{}");
    await().atMost(Duration.ofSeconds(5)).until(() -> depth(RabbitTopology.UNROUTED) == 1);
  }

  private static int depth(String queue) {
    Integer count = rabbit.execute(channel -> channel.queueDeclarePassive(queue).getMessageCount());
    return count == null ? -1 : count;
  }

  private void publishDirect(IntegrationEvent event) {
    OutboxWriter writer = spring.getBean(OutboxWriter.class);
    transactions.executeWithoutResult(s -> writer.append(event));
    new OutboxPublisher(db, new RabbitMessageSink(rabbit, 5000), 10, 5, Duration.ofSeconds(60)).publishBatch();
  }
}
