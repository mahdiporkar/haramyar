package ir.haramyar.rozeh.application;

/**
 * Identity-related preconditions for submitting a request. Whether a verified mobile number is required before the
 * first booking is an open business decision; it defaults to not required because no SMS verification channel is
 * part of the first release, and an unverified number is never treated as verified.
 */
public record BookingAdmissionPolicy(boolean requireVerifiedMobile) {}
