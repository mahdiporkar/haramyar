package ir.haramyar.rozeh.application;

/** No booking exists that the caller may see. Also used for other hosts' bookings so existence is not disclosed. */
public final class BookingNotFound extends RuntimeException {
  public BookingNotFound() { super("Booking not found"); }
}
