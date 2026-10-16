package ir.haramyar.rozeh.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BookingJpaRepository extends JpaRepository<BookingEntity, UUID> {
  Optional<BookingEntity> findByHostIdAndClientRequestId(UUID hostId, UUID clientRequestId);

  boolean existsByTrackingCode(String trackingCode);

  @Query("""
      select b from BookingEntity b
      where b.hostId = :host and (:allStatuses = true or b.status in :statuses)
      order by b.requestedAt desc""")
  Page<BookingEntity> byHost(@Param("host") UUID host, @Param("allStatuses") boolean allStatuses, @Param("statuses") List<String> statuses, Pageable page);

  @Query("""
      select b from BookingEntity b
      where (:allStatuses = true or b.status in :statuses)
        and (:anyCity = true or lower(b.city) = lower(:city))
        and b.startsAt >= :from and b.startsAt < :to
      order by b.startsAt asc""")
  Page<BookingEntity> forCoordination(@Param("allStatuses") boolean allStatuses, @Param("statuses") List<String> statuses, @Param("anyCity") boolean anyCity,
      @Param("city") String city, @Param("from") Instant from, @Param("to") Instant to, Pageable page);

  @Query("""
      select count(b) > 0 from BookingEntity b
      where b.providerId = :provider and b.status = 'CONFIRMED' and b.id <> :excluding
        and b.startsAt < :end and :start < b.endsAt""")
  boolean confirmedOverlapExists(@Param("provider") UUID provider, @Param("start") Instant start, @Param("end") Instant end, @Param("excluding") UUID excluding);

  @Query("""
      select b from BookingEntity b
      where b.providerId = :provider and b.status = 'CONFIRMED' and b.startsAt < :to and :from < b.endsAt
      order by b.startsAt""")
  List<BookingEntity> confirmedSlots(@Param("provider") UUID provider, @Param("from") Instant from, @Param("to") Instant to);
}

interface BookingHistoryJpaRepository extends JpaRepository<BookingHistoryEntity, Long> {
  List<BookingHistoryEntity> findByBookingIdOrderBySequenceNo(UUID bookingId);
}

interface ProviderJpaRepository extends JpaRepository<ProviderEntity, UUID> {
  List<ProviderEntity> findAllByOrderByDisplayName();

  List<ProviderEntity> findByActiveTrueOrderByDisplayName();
}
