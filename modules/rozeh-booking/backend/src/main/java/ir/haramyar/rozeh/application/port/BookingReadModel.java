package ir.haramyar.rozeh.application.port;

import ir.haramyar.rozeh.application.query.BookingSummary;
import ir.haramyar.rozeh.application.query.Page;
import ir.haramyar.rozeh.application.query.ProviderSlot;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import ir.haramyar.rozeh.domain.model.HostId;
import ir.haramyar.rozeh.domain.model.ProviderId;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Query-side port: lists and calendars without loading aggregates. */
public interface BookingReadModel {
  Page<BookingSummary> byHost(HostId host, Set<BookingStatus> statuses, int page, int size);

  Page<BookingSummary> forCoordination(Set<BookingStatus> statuses, String city, Instant from, Instant to, int page, int size);

  List<ProviderSlot> confirmedSlots(ProviderId provider, Instant from, Instant to);
}
