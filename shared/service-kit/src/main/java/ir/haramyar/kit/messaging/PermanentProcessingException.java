package ir.haramyar.kit.messaging;

/**
 * The event can never be processed successfully (malformed, unknown contract version, violated invariant). It goes
 * straight to the dead-letter queue instead of being retried. Every other exception is treated as transient.
 */
public class PermanentProcessingException extends RuntimeException {
  public PermanentProcessingException(String message) { super(message); }
  public PermanentProcessingException(String message, Throwable cause) { super(message, cause); }
}
