package ir.haramyar.martyrs.application;

import java.io.InputStream;

/** Object storage port; the MinIO implementation remains inside infrastructure. */
public interface BookObjectStore {
  void put(String key, InputStream stream, long size);
  InputStream open(String key);
}
