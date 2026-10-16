package ir.haramyar.rozeh.application.port;

/** The aggregate was changed by another operation after it was read; the caller should reload and retry. */
public final class ConcurrentModification extends RuntimeException {
  public ConcurrentModification() { super("The booking changed concurrently; reload and retry"); }
}
