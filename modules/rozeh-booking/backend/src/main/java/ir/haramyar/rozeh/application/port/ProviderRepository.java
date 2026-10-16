package ir.haramyar.rozeh.application.port;

import ir.haramyar.rozeh.domain.model.Provider;
import ir.haramyar.rozeh.domain.model.ProviderId;
import java.util.List;
import java.util.Optional;

public interface ProviderRepository {
  Optional<Provider> findById(ProviderId id);

  List<Provider> list(boolean includeInactive);

  void add(Provider provider);

  /** @throws ConcurrentModification when the provider changed since it was loaded */
  void update(Provider provider);
}
