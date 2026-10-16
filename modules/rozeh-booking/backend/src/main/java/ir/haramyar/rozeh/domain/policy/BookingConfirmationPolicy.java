package ir.haramyar.rozeh.domain.policy;

/**
 * Preconditions of final confirmation beyond the state machine. Whether a provider must be assigned before a booking
 * can be confirmed belongs to the unresolved provider-assignment decision and is therefore configurable.
 */
public record BookingConfirmationPolicy(boolean providerRequired) {}
