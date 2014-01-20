package com.tools.io;

import com.google.common.base.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

public class ConsensusMethylationCallReader implements MethylationCallFormat, Closeable {
  public final SequenceDictionary sequenceDictionary;

  private final BufferedLineReader lineIterator;
  private Optional<ConsensusCall> bufferedCall;

  /**
   * Constructs a ConsensusCallReader around the provided InputStream.
   *
   * @param inputStream   the InputStream to read the methylation call lines from
   *
   * @throws java.io.IOException if it is unable to read from the InputStream
   */
  public ConsensusMethylationCallReader(InputStream inputStream) throws IOException {
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
   * @throws java.util.NoSuchElementException if the next entry cannot be parsed
   */
  public ConsensusCall next() {
    ConsensusCall nextCall = bufferedCall.isPresent() ? bufferedCall.get() : parseNext(lineIterator.next());
    bufferedCall = Optional.absent();

    return nextCall;
  }

  public ConsensusCall peek() {
    // Ensure that the buffer if populated
    if (!bufferedCall.isPresent()) bufferedCall = Optional.of(parseNext(lineIterator.next()));

    return bufferedCall.get();
  }

  private SequenceDictionary parseHeader(BufferedLineReader lineReader) {
    // Validate that the expected pragma is found
    if (!lineReader.next().equals("###cmethylcf")) {
      throw new IllegalArgumentException("expected consensus methylation call format");
    }

    // Parse the sequence dictionary
    LinkedHashMap<String, Integer> sequenceDictionary = new LinkedHashMap<>();
    while (lineReader.hasNext() && lineReader.peek().startsWith("#seq")) {
      String line = lineReader.next();
      String[] fields = FIELD_PATTERN.split(line);
      sequenceDictionary.put(fields[1], Integer.parseInt(fields[2]));
    }

    // Discard the column headers
    lineReader.next();

    return new SequenceDictionary(sequenceDictionary, new ArrayList<String>());
  }

  private ConsensusCall parseNext(String line) {
    String[] fields = FIELD_PATTERN.split(line);

    return new ConsensusCall(
      fields[0],
      Integer.parseInt(fields[1]),
      fields[2].charAt(0),
      Double.parseDouble(fields[3]),
      Double.parseDouble(fields[4]),
      Integer.parseInt(fields[5]),
      Integer.parseInt(fields[6])
    );
  }
}
