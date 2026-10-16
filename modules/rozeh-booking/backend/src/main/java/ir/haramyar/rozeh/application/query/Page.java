package ir.haramyar.rozeh.application.query;

import java.util.List;

public record Page<T>(List<T> items, int page, int size, long total) {
  public Page {
    items = List.copyOf(items);
  }
}
