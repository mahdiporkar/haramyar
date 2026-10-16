package ir.haramyar.rozeh.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider")
class ProviderEntity {
  @Id UUID id;
  @Column(name = "display_name", nullable = false) String displayName;
  @Column(nullable = false) boolean active;
  @Column(name = "registered_at", nullable = false, updatable = false) Instant registeredAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;
  @Version long version;
}
