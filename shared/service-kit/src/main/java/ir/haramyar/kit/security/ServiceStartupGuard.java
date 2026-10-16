package ir.haramyar.kit.security;

import io.hiveplatform.spring.ProductionGuard;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

/**
 * Fail-closed startup check for HaramYar services, using Hive's {@link ProductionGuard}. In {@code HARAMYAR_PROFILE=production}
 * (the default) the service refuses to start with an insecure identity issuer, a weak database password or a missing
 * broker password; {@code development} only logs the violations.
 */
public class ServiceStartupGuard implements SmartInitializingSingleton {
  private final Environment environment;
  private final HaramyarSecurityProperties security;

  public ServiceStartupGuard(Environment environment, HaramyarSecurityProperties security) {
    this.environment = environment;
    this.security = security;
  }

  @Override
  public void afterSingletonsInstantiated() {
    String service = environment.getProperty("spring.application.name", "haramyar-service");
    String issuer = security.issuer() == null ? "" : security.issuer();
    String dbUrl = environment.getProperty("spring.datasource.url", "");
    String dbPassword = environment.getProperty("spring.datasource.password", "");
    String rabbitHost = environment.getProperty("spring.rabbitmq.host", "");
    String rabbitPassword = environment.getProperty("spring.rabbitmq.password", "");
    new ProductionGuard(environment.getProperty("haramyar.profile", "production"))
        .rule("identity issuer must use HTTPS", () -> !issuer.startsWith("https://"))
        .rule("database password must contain at least 16 characters", () -> !dbUrl.isBlank() && dbPassword.length() < 16)
        .rule("broker password must contain at least 16 characters", () -> !rabbitHost.isBlank() && rabbitPassword.length() < 16)
        .enforce(service);
  }
}
