package ir.haramyar.rozeh.interfaces.rest;

import ir.haramyar.rozeh.application.BookingCommandHandlers;
import ir.haramyar.rozeh.application.BookingQueryHandlers;
import ir.haramyar.rozeh.application.command.BookingCommands;
import ir.haramyar.rozeh.application.query.BookingSummary;
import ir.haramyar.rozeh.application.query.BookingView;
import ir.haramyar.rozeh.application.query.Page;
import ir.haramyar.rozeh.domain.model.BookingStatus;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Host self-service. Every operation acts only on bookings owned by the authenticated host. */
@RestController
@RequestMapping("/my/bookings")
class MyBookingsController {
  private final BookingCommandHandlers commands;
  private final BookingQueryHandlers queries;
  private final Actors actors;

  MyBookingsController(BookingCommandHandlers commands, BookingQueryHandlers queries, Actors actors) {
    this.commands = commands;
    this.queries = queries;
    this.actors = actors;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  BookingView submit(@Valid @RequestBody Requests.SubmitBooking body) {
    return commands.handle(new BookingCommands.RequestBooking(actors.host(), actors.mobileVerified(), body.clientRequestId(), body.hostName(), body.hostPhone(),
        body.city(), body.area(), body.address(), body.category(), body.audience(), body.deliveryMode(), body.approximateCapacity(), body.notes(),
        body.startsAt().toInstant(), body.durationMinutes(), zone(body.timeZone())));
  }

  @GetMapping
  Page<BookingSummary> mine(@RequestParam(required = false) Set<BookingStatus> status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return queries.getMyBookings(actors.host(), status == null ? EnumSet.noneOf(BookingStatus.class) : status, page, size);
  }

  @GetMapping("/{bookingId}")
  BookingView details(@PathVariable UUID bookingId) {
    return queries.getBookingDetails(actors.host(), bookingId);
  }

  @PostMapping("/{bookingId}/confirm")
  BookingView confirm(@PathVariable UUID bookingId, @Valid @RequestBody(required = false) Requests.Transition body) {
    return commands.handle(new BookingCommands.ConfirmBooking(actors.host(), bookingId, body == null ? null : body.expectedVersion()));
  }

  @PostMapping("/{bookingId}/cancel")
  BookingView cancel(@PathVariable UUID bookingId, @Valid @RequestBody(required = false) Requests.Transition body) {
    return commands.handle(new BookingCommands.CancelBooking(actors.host(), bookingId, body == null ? null : body.expectedVersion(), body == null ? null : body.reason()));
  }

  static ZoneId zone(String id) {
    try {
      return ZoneId.of(id);
    } catch (DateTimeException invalid) {
      throw new IllegalArgumentException("Unknown time zone");
    }
  }
}
