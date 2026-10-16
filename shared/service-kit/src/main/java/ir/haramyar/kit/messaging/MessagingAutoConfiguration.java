package ir.haramyar.kit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/** Outbox writer and inbox for every HaramYar service with a database. */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnBean(JdbcTemplate.class)
public class MessagingAutoConfiguration {
  @Bean OutboxWriter outboxWriter(JdbcTemplate db, ObjectMapper json) { return new OutboxWriter(db, json); }

  @Bean Inbox inbox(JdbcTemplate db) { return new Inbox(db); }
}
