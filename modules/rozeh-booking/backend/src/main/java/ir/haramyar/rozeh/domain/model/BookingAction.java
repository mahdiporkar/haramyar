package ir.haramyar.rozeh.domain.model;

/** Operations that change a booking; each is recorded in the booking history. */
public enum BookingAction { REQUEST, ACCEPT, REJECT, ASSIGN_PROVIDER, CONFIRM, CANCEL, COMPLETE }
