package com.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

  /**
   * Returns a Path to a temporary directory that will be deleted when the JVM exits.
   *
   * @return a Path to a temporary directory
   * @throws IOException if the temp directory cannot be created
   */
  public static Path createTempDir() throws IOException {
    Path path = Files.createTempDirectory("temp");
    path.toFile().deleteOnExit();

    return path;
  }

  /**
   * Returns a Path to a temporary file that will be deleted when the JVM exits.
   *
   * @return a Path to a temporary file
   * @throws IOException if the temp file cannot be created
   */
  public static Path createTempFile(String extension) throws IOException {
    Path path = Files.createTempFile("temp", "." + extension);
    path.toFile().deleteOnExit();

    return path;
  }
}
