package ir.haramyar.rozeh.interfaces.rest;

import ir.haramyar.rozeh.application.BookingCommandHandlers;
import ir.haramyar.rozeh.application.BookingQueryHandlers;
import ir.haramyar.rozeh.application.ProviderHandlers;
import ir.haramyar.rozeh.application.command.BookingCommands;
import ir.haramyar.rozeh.application.query.BookingSummary;
import ir.haramyar.rozeh.application.query.BookingView;
import ir.haramyar.rozeh.application.query.Page;
import ir.haramyar.rozeh.application.query.ProviderSlot;
import ir.haramyar.rozeh.application.query.ProviderView;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coordination workspace. The Hive BFF admits a request here only for users holding the matching
 * {@code rozeh-booking.coordination} / {@code rozeh-booking.providers} action; this controller additionally requires a
 * staff account and lets the domain policies decide which transition the coordinator capacity may perform.
 */
@RestController
@RequestMapping("/coordination")
class CoordinationController {
  private final BookingCommandHandlers commands;
  private final BookingQueryHandlers queries;
  private final ProviderHandlers providers;
  private final Actors actors;

  CoordinationController(BookingCommandHandlers commands, BookingQueryHandlers queries, ProviderHandlers providers, Actors actors) {
    this.commands = commands;
    this.queries = queries;
    this.providers = providers;
    this.actors = actors;
  }

  @GetMapping("/bookings")
  Page<BookingSummary> search(@RequestParam(required = false) Set<BookingStatus> status, @RequestParam(required = false) String city,
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    actors.requireStaff();
    return queries.searchBookingsForCoordination(status == null ? EnumSet.noneOf(BookingStatus.class) : status, city, from, to, page, size);
  }

  @GetMapping("/bookings/{bookingId}")
  BookingView details(@PathVariable UUID bookingId) {
    return queries.getBookingDetails(actors.coordinator(), bookingId);
  }

  @PostMapping("/bookings/{bookingId}/accept")
  BookingView accept(@PathVariable UUID bookingId, @Valid @RequestBody(required = false) Requests.Transition body) {
    return commands.handle(new BookingCommands.AcceptBooking(actors.coordinator(), bookingId, version(body), reason(body)));
  }

  @PostMapping("/bookings/{bookingId}/reject")
  BookingView reject(@PathVariable UUID bookingId, @Valid @RequestBody Requests.Transition body) {
    return commands.handle(new BookingCommands.RejectBooking(actors.coordinator(), bookingId, version(body), reason(body)));
  }

  @PostMapping("/bookings/{bookingId}/provider")
  BookingView assign(@PathVariable UUID bookingId, @Valid @RequestBody Requests.Assignment body) {
    return commands.handle(new BookingCommands.AssignProvider(actors.coordinator(), bookingId, body.expectedVersion(), body.providerId()));
  }

  @PostMapping("/bookings/{bookingId}/confirm")
  BookingView confirm(@PathVariable UUID bookingId, @Valid @RequestBody(required = false) Requests.Transition body) {
    return commands.handle(new BookingCommands.ConfirmBooking(actors.coordinator(), bookingId, version(body)));
  }

  @PostMapping("/bookings/{bookingId}/cancel")
  BookingView cancel(@PathVariable UUID bookingId, @Valid @RequestBody Requests.Transition body) {
    return commands.handle(new BookingCommands.CancelBooking(actors.coordinator(), bookingId, version(body), reason(body)));
  }

  @PostMapping("/bookings/{bookingId}/complete")
  BookingView complete(@PathVariable UUID bookingId, @Valid @RequestBody(required = false) Requests.Transition body) {
    return commands.handle(new BookingCommands.CompleteBooking(actors.coordinator(), bookingId, version(body)));
  }

  @GetMapping("/providers")
  List<ProviderView> providers(@RequestParam(defaultValue = "false") boolean includeInactive) {
    actors.requireStaff();
    return providers.list(includeInactive);
  }

  @PostMapping("/providers")
  @ResponseStatus(HttpStatus.CREATED)
  ProviderView register(@Valid @RequestBody Requests.NewProvider body) {
    actors.requireStaff();
    return providers.handle(new BookingCommands.RegisterProvider(body.displayName()));
  }

  @PutMapping("/providers/{providerId}")
  ProviderView update(@PathVariable UUID providerId, @Valid @RequestBody Requests.ProviderChange body) {
    actors.requireStaff();
    return providers.handle(new BookingCommands.UpdateProvider(providerId, body.expectedVersion(), body.displayName(), body.active()));
  }

  @GetMapping("/providers/{providerId}/availability")
  List<ProviderSlot> availability(@PathVariable UUID providerId, @RequestParam Instant from, @RequestParam Instant to) {
    actors.requireStaff();
    return queries.getProviderAvailability(providerId, from, to);
  }

  private static Long version(Requests.Transition body) { return body == null ? null : body.expectedVersion(); }
  private static String reason(Requests.Transition body) { return body == null ? null : body.reason(); }
}
