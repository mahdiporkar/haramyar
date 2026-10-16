package ir.haramyar.martyrs.infrastructure.config;

import ir.haramyar.martyrs.application.EditorialHandlers;
import ir.haramyar.martyrs.application.Ports;
import ir.haramyar.martyrs.application.PublicQueries;
import ir.haramyar.martyrs.domain.EditorialPolicy;
import ir.haramyar.martyrs.infrastructure.storage.S3MediaStorage;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class MartyrsConfiguration {
  @Bean @ConditionalOnMissingBean Clock clock() { return Clock.systemUTC(); }

  /** Four-eyes publication is the proposed editorial policy (pending business confirmation). */
  @Bean EditorialPolicy editorialPolicy(@Value("${haramyar.martyrs.four-eyes:true}") boolean fourEyes) { return new EditorialPolicy(fourEyes); }

  @Bean @ConditionalOnMissingBean(Ports.MediaStorage.class)
  Ports.MediaStorage mediaStorage(@Value("${haramyar.martyrs.storage.endpoint}") String endpoint, @Value("${haramyar.martyrs.storage.region:us-east-1}") String region,
      @Value("${haramyar.martyrs.storage.access-key}") String accessKey, @Value("${haramyar.martyrs.storage.secret-key}") String secretKey,
      @Value("${haramyar.martyrs.storage.bucket:haramyar-martyrs}") String bucket) {
    var storage = new S3MediaStorage(endpoint, region, accessKey, secretKey, bucket);
    storage.ensureBucket();
    return storage;
  }

  @Bean EditorialHandlers editorialHandlers(Ports.ProfileRepository profiles, Ports.EditorialIndex index, Ports.MediaCatalog catalog, Ports.MediaStorage storage,
      EditorialPolicy policy, PlatformTransactionManager tx, Clock clock) {
    return new EditorialHandlers(profiles, index, catalog, storage, policy, new TransactionTemplate(tx), clock);
  }

  @Bean PublicQueries publicQueries(Ports.ProfileRepository profiles, Ports.PublishedProfiles published, Ports.MediaCatalog catalog, Ports.MediaStorage storage,
      PlatformTransactionManager tx) {
    var readOnly = new TransactionTemplate(tx);
    readOnly.setReadOnly(true);
    return new PublicQueries(profiles, published, catalog, storage, readOnly);
  }
}
