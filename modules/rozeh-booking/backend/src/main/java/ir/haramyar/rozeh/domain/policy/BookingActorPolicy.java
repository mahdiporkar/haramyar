package ir.haramyar.rozeh.domain.policy;

import ir.haramyar.rozeh.domain.model.BookingAction;
import ir.haramyar.rozeh.domain.model.BookingActor;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which actor capacity may perform which booking operation. The responsible actor for acceptance and final
 * confirmation is not yet conclusively approved; this policy isolates that decision so it can change through
 * configuration without touching the aggregate. Hosts always act only on their own bookings (enforced by the aggregate).
 */
public final class BookingActorPolicy {
  private final Map<BookingAction, Set<BookingActor.Role>> permitted;

  public BookingActorPolicy(Map<BookingAction, Set<BookingActor.Role>> permitted) {
    var copy = new EnumMap<BookingAction, Set<BookingActor.Role>>(BookingAction.class);
    permitted.forEach((action, roles) -> copy.put(action, Set.copyOf(roles)));
    this.permitted = Map.copyOf(copy);
  }

  /**
   * Provisional defaults: hosts request and may cancel their own bookings; coordinators accept, reject, assign providers
   * and complete; final confirmation is performed by {@code confirmationRoles} (the prototype shows the host confirming).
   */
  public static BookingActorPolicy provisional(Set<BookingActor.Role> confirmationRoles) {
    return new BookingActorPolicy(Map.of(
        BookingAction.REQUEST, EnumSet.of(BookingActor.Role.HOST),
        BookingAction.ACCEPT, EnumSet.of(BookingActor.Role.COORDINATOR),
        BookingAction.REJECT, EnumSet.of(BookingActor.Role.COORDINATOR),
        BookingAction.ASSIGN_PROVIDER, EnumSet.of(BookingActor.Role.COORDINATOR),
        BookingAction.CONFIRM, confirmationRoles.isEmpty() ? EnumSet.noneOf(BookingActor.Role.class) : EnumSet.copyOf(confirmationRoles),
        BookingAction.CANCEL, EnumSet.of(BookingActor.Role.HOST, BookingActor.Role.COORDINATOR),
        BookingAction.COMPLETE, EnumSet.of(BookingActor.Role.COORDINATOR)));
  }

  public boolean permits(BookingAction action, BookingActor.Role role) {
    return permitted.getOrDefault(action, Set.of()).contains(role);
  }
}
