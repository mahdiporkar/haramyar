package ir.haramyar.rozeh.application.query;

import ir.haramyar.rozeh.domain.model.BookingAction;
import ir.haramyar.rozeh.domain.model.BookingActor;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Full booking as shown to its host or an authorized coordinator. {@code allowedActions} tells the viewer which
 * operations the policies currently permit them (an affordance; every command is re-checked).
 */
public record BookingView(UUID id, String trackingCode, BookingStatus status, Host host, Location location, Details details, Time time, Provider provider,
    Instant requestedAt, Instant updatedAt, long version, List<History> history, Set<BookingAction> allowedActions) {

  public record Host(UUID hostId, String name, String phone) {}
  public record Location(String city, String area, String address) {}
  public record Details(String category, String audience, String deliveryMode, Integer approximateCapacity, String notes) {}
  public record Time(Instant startsAt, Instant endsAt, String timeZone) {}
  public record Provider(UUID id, String displayName) {}
  public record History(BookingAction action, BookingStatus from, BookingStatus to, BookingActor.Role actorRole, boolean byViewer, String detail, Instant at) {}
}
