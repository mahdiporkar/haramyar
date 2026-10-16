package ir.haramyar.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.haramyar.kit.messaging.Inbox;
import ir.haramyar.kit.messaging.RabbitTopology;
import ir.haramyar.kit.messaging.ReliableEventConsumer;
import ir.haramyar.shell.notification.BookingNotificationHandler;
import ir.haramyar.shell.onboarding.HiveAccessClient;
import java.time.Clock;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

/** HaramYar application backend: occasions/themes, onboarding and notifications. */
@SpringBootApplication
public class ShellServiceApplication {
  public static final String NOTIFICATION_QUEUE = "haramyar.shell.notifications";

  public static void main(String[] args) {
    SpringApplication.run(ShellServiceApplication.class, args);
  }

  @Bean @ConditionalOnMissingBean Clock clock() { return Clock.systemUTC(); }

  @Bean
  HiveAccessClient hiveAccessClient(@Value("${haramyar.hive.authorization-url}") String url, @Value("${haramyar.hive.provisioning-password:}") String password,
      @Value("${haramyar.hive.default-role:haramyar-user}") String role, ObjectMapper json) {
    return new HiveAccessClient(url, password, role, json);
  }

  @Bean Declarables notificationTopology() {
    return new Declarables(RabbitTopology.consumer(NOTIFICATION_QUEUE, List.of("haramyar.booking.*.v1")));
  }

  @Bean
  @ConditionalOnProperty(name = "haramyar.notifications.consumer.enabled", havingValue = "true", matchIfMissing = true)
  SimpleMessageListenerContainer notificationListener(ConnectionFactory connections, RabbitTemplate rabbit, Inbox inbox, PlatformTransactionManager tx, JdbcTemplate db, ObjectMapper json) {
    var container = new SimpleMessageListenerContainer(connections);
    container.setQueueNames(NOTIFICATION_QUEUE);
    container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    container.setPrefetchCount(10);
    container.setMessageListener(new ReliableEventConsumer("shell.notifications", NOTIFICATION_QUEUE, inbox, new TransactionTemplate(tx), rabbit, json,
        new BookingNotificationHandler(db)));
    return container;
  }
}
