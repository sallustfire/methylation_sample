package com.tools.io;

import com.google.common.base.Optional;
import com.google.common.collect.PeekingIterator;

import java.io.*;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

/**
 * A PeekingIterator over the lines in the provided InputStream.
 */
public class BufferedLineReader implements PeekingIterator<String>, Closeable {
  private BufferedReader reader;
  private Optional<String> bufferedLine;

  /**
   * Constructs a BufferedLineReader for the provided InputStream.
   *
   * @param inputStream   an InputStream form which to read
   *
   * @throws IOException if the InputStream cannot be read
   */
  public BufferedLineReader(InputStream inputStream) throws IOException {
    // Ensure that input stream can be reset
    if (!inputStream.markSupported()) {
      inputStream = new BufferedInputStream(inputStream);
    }
    inputStream.mark(2);

    // Read the first two byes to see if they are the gzip magic header
    int magicHeader = inputStream.read() & 0xFF | ((inputStream.read() << 8) & 0xFF00);
    inputStream.reset();

    InputStream processedInputStream;
    if (magicHeader == GZIPInputStream.GZIP_MAGIC) {
      // Treat the input as gzip
      processedInputStream = new GZIPInputStream(inputStream);
    } else {
      processedInputStream = inputStream;
    }

    this.reader = new BufferedReader(new InputStreamReader(processedInputStream));
    this.bufferedLine = Optional.absent();
  }

  /**
   * Closes the underlying IO.
   */
  public void close() {
    try {
      reader.close();
    } catch (IOException exception) {
      exception.printStackTrace();
    }
  }

  @Override
  public boolean hasNext() {
    boolean result;
    try {
      result = bufferedLine.isPresent() || loadBuffer().isPresent();
    } catch (IOException exception) {
      result = false;
    }

    return result;
  }

  @Override
  public String next() throws NoSuchElementException {
    // Ensure that the buffer is populated
    try {
      if (!bufferedLine.isPresent()) loadBuffer();
    } catch (IOException exception) {
      throw new NoSuchElementException("unable to read next element");
    }

    String nextLine;
    if (bufferedLine.isPresent()) {
      nextLine = bufferedLine.get();
      bufferedLine = Optional.absent();
    } else {
      throw new NoSuchElementException();
    }

    return nextLine;
  }

  @Override
  public String peek() {
    String nextLine;
    if (hasNext()) nextLine = bufferedLine.get();
    else {
      throw new NoSuchElementException();
    }

    return nextLine;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("remove not supported");
  }

  private Optional<String> loadBuffer() throws IOException {
    bufferedLine = Optional.fromNullable(reader.readLine());
    return bufferedLine;
  }
}
