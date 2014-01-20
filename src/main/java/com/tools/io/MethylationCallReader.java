package com.tools.io;

import com.google.common.base.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class MethylationCallReader implements MethylationCallFormat, Closeable {
  public final SequenceDictionary sequenceDictionary;

  private final BufferedLineReader lineIterator;
  private Optional<MethylationCall> bufferedCall;

  /**
   * Constructs a MethylationCallReader around the provided InputStream.
   *
   * @param inputStream   the InputStream to read the methylation call lines from
   *
   * @throws IOException if it is unable to read from the InputStream
   */
  public MethylationCallReader(InputStream inputStream) throws IOException {
    this.lineIterator = new BufferedLineReader(inputStream);
    this.sequenceDictionary = parseHeader(lineIterator);
    this.bufferedCall = Optional.absent();
  }

  /**
   * Closes the underlying reader.
   */
  @Override
  public void close() { lineIterator.close(); }

  /**
   * Returns a boolean indicating if there is another entry to be read.
   *
   * @return  a boolean
   */
  public boolean hasNext() { return lineIterator.hasNext(); }

  /**
   * Returns the next entry.  If this reader has read all of the entries, it throws an exception.
   *
   * @return the next entry in the InputStream
   *
   * @throws NoSuchElementException if the next entry cannot be parsed
   */
  public MethylationCall next() {
    MethylationCall nextCall = bufferedCall.isPresent() ? bufferedCall.get() : parseNext(lineIterator.next());
    bufferedCall = Optional.absent();

    return nextCall;
  }

  public MethylationCall peek() {
    // Ensure that the buffer if populated
    if (!bufferedCall.isPresent()) bufferedCall = Optional.of(parseNext(lineIterator.next()));

    return bufferedCall.get();
  }

  private SequenceDictionary parseHeader(BufferedLineReader lineReader) {
    // Validate that the expected pragma is found
    if (!lineReader.next().equals(FORMAT_IDENTIFIER)) {
      throw new IllegalArgumentException("expected methylation call format");
    }

    // Parse the control contigs
    ArrayList<String> controlContigs = new ArrayList<>();
    LinkedHashMap<String, Integer> sequenceDictionary = new LinkedHashMap<>();
    while (lineReader.hasNext() && lineReader.peek().startsWith(PRAGMA)) {
      String line = lineReader.next();
      String[] fields = FIELD_PATTERN.split(line);

      switch (fields[0]) {
        case CONTROL:
          controlContigs.add(fields[1]);
          break;
        case SEQUENCE:
          sequenceDictionary.put(fields[1], Integer.parseInt(fields[2]));
          break;
      }
    }

    return new SequenceDictionary(sequenceDictionary, controlContigs);
  }

  private MethylationCall parseNext(String line) {
    String[] fields = FIELD_PATTERN.split(line);
    int position = Integer.parseInt(fields[1]);
    int methylatedCount = Integer.parseInt(fields[3]);
    int totalCount = Integer.parseInt(fields[4]);
    Optional<Double> ratio = fields.length > 5 ? Optional.of(Double.parseDouble(fields[5])) : Optional.<Double>absent();

    return new MethylationCall(fields[0], position, fields[2].charAt(0), methylatedCount, totalCount, ratio);
  }
}
