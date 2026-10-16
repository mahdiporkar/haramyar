package ir.haramyar.rozeh.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence representation of a booking; mapped explicitly to and from the domain aggregate. */
@Entity
@Table(name = "booking")
class BookingEntity {
  @Id UUID id;
  @Column(name = "tracking_code", nullable = false, updatable = false) String trackingCode;
  @Column(name = "host_id", nullable = false, updatable = false) UUID hostId;
  @Column(name = "client_request_id", nullable = false, updatable = false) UUID clientRequestId;
  @Column(name = "host_name", nullable = false) String hostName;
  @Column(name = "host_phone", nullable = false) String hostPhone;
  @Column(nullable = false) String city;
  String area;
  @Column(nullable = false) String address;
  @Column(nullable = false) String category;
  String audience;
  @Column(name = "delivery_mode", nullable = false) String deliveryMode;
  @Column(name = "approximate_capacity") Integer approximateCapacity;
  String notes;
  @Column(name = "starts_at", nullable = false) Instant startsAt;
  @Column(name = "ends_at", nullable = false) Instant endsAt;
  @Column(name = "time_zone", nullable = false) String timeZone;
  @Column(nullable = false) String status;
  @Column(name = "provider_id") UUID providerId;
  @Column(name = "requested_at", nullable = false, updatable = false) Instant requestedAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;
  @Version long version;
}
