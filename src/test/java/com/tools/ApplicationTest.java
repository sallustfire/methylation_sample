package com.tools;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class ApplicationTest {

  /**
   * Returns a Path to a temporary directory that will be deleted when the JVM exits.
   *
   * @return the Path to the temp directory
   * @throws IOException if the temp directory cannot be created
   */
  protected Path createTempDirectory() throws IOException {
    Path outputPath = Files.createTempDirectory("temp");
    outputPath.toFile().deleteOnExit();

    return outputPath;
  }

  /**
   * Returns a Path to a temporary file that will be deleted when the JVM exits.
   *
   * @return the Path to the temp file
   * @throws IOException if the temp file cannot be created
   */
  protected Path createTempFile(String extension) throws IOException {
    String adjustedExtension = extension.equals("") ? "" : "." + extension;
    Path outputPath = Files.createTempFile("temp", adjustedExtension);
    outputPath.toFile().deleteOnExit();

    return outputPath;
  }

  /**
   * Returns a File to the specified test resource.
   *
   * @param path  the String path to the resource
   * @return  a Path for the resource
   * @throws URISyntaxException if the resource cannot be found
   */
  protected Path getFileResource(String path) throws URISyntaxException {
    URL url = ApplicationTest.class.getResource("/com/tools" + path);
    return Paths.get(url.toURI()).toAbsolutePath();
  }

  protected boolean contentEquals(Path path1, Path path2) throws IOException {
    File file1 = new File(path1.toUri());
    File file2 = new File(path2.toUri());

    return FileUtils.contentEquals(file1, file2);
  }

  /**
   * Returns true if the lines in the files are identical.
   *
   * @param path1 a Path to a file to compare
   * @param path2 a Path to a file to compare
   *
   * @return a boolean indicating if the files have equivalent lines
   *
   * @throws IOException if either of the files cannot be read
   */
  protected boolean linesEquals(Path path1, Path path2) throws IOException {
    List<String> lines1 = Files.readAllLines(path1, Charset.defaultCharset());
    List<String> lines2 = Files.readAllLines(path2, Charset.defaultCharset());

    return lines1.equals(lines2);
  }
}
