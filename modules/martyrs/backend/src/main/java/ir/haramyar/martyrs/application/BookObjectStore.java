package ir.haramyar.martyrs.application;

import java.io.InputStream;
import java.nio.file.Path;

/** Object storage port; the MinIO implementation remains inside infrastructure. */
public interface BookObjectStore {
  /** A repeatable source lets the S3 client retry without buffering PDFs on the heap. */
  void put(String key, Path file);
  InputStream open(String key);
}
