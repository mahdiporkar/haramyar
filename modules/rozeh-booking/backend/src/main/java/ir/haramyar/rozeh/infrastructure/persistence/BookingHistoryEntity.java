package ir.haramyar.rozeh.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "booking_history")
class BookingHistoryEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
  @Column(name = "booking_id", nullable = false) UUID bookingId;
  @Column(name = "sequence_no", nullable = false) int sequenceNo;
  @Column(nullable = false) String action;
  @Column(name = "from_status") String fromStatus;
  @Column(name = "to_status", nullable = false) String toStatus;
  @Column(name = "actor_id", nullable = false) UUID actorId;
  @Column(name = "actor_role", nullable = false) String actorRole;
  String detail;
  @Column(name = "occurred_at", nullable = false) Instant occurredAt;
}
