package ir.haramyar.rozeh.infrastructure.persistence;

import ir.haramyar.rozeh.application.port.ConcurrentModification;
import ir.haramyar.rozeh.application.port.ProviderRepository;
import ir.haramyar.rozeh.domain.model.Provider;
import ir.haramyar.rozeh.domain.model.ProviderId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class JpaProviderRepository implements ProviderRepository {
  private final ProviderJpaRepository rows;

  JpaProviderRepository(ProviderJpaRepository rows) { this.rows = rows; }

  @Override
  public Optional<Provider> findById(ProviderId id) {
    return rows.findById(id.value()).map(JpaProviderRepository::toDomain);
  }

  @Override
  public List<Provider> list(boolean includeInactive) {
    return (includeInactive ? rows.findAllByOrderByDisplayName() : rows.findByActiveTrueOrderByDisplayName()).stream().map(JpaProviderRepository::toDomain).toList();
  }

  @Override
  public void add(Provider provider) {
    ProviderEntity entity = new ProviderEntity();
    entity.id = provider.id().value();
    entity.registeredAt = provider.registeredAt();
    entity.displayName = provider.displayName();
    entity.active = provider.active();
    entity.updatedAt = Instant.now();
    rows.saveAndFlush(entity);
  }

  @Override
  public void update(Provider provider) {
    ProviderEntity entity = rows.findById(provider.id().value()).orElseThrow(ConcurrentModification::new);
    if (entity.version != provider.version()) throw new ConcurrentModification();
    entity.displayName = provider.displayName();
    entity.active = provider.active();
    entity.updatedAt = Instant.now();
    try {
      rows.saveAndFlush(entity);
    } catch (OptimisticLockingFailureException stale) {
      throw new ConcurrentModification();
    }
  }

  private static Provider toDomain(ProviderEntity e) {
    return Provider.reconstitute(new ProviderId(e.id), e.displayName, e.active, e.registeredAt, e.version);
  }
}
