package ir.haramyar.rozeh.application.query;

import ir.haramyar.rozeh.domain.model.Provider;
import java.time.Instant;
import java.util.UUID;

public record ProviderView(UUID id, String displayName, boolean active, Instant registeredAt, long version) {
  public static ProviderView of(Provider provider) {
    return new ProviderView(provider.id().value(), provider.displayName(), provider.active(), provider.registeredAt(), provider.version());
  }
}
