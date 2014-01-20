package com.tools.io;

import com.google.common.base.Joiner;

import java.io.Closeable;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MethylationCallWriter implements MethylationCallFormat, Closeable {
  private final Joiner joiner = Joiner.on(FIELD_DELIMITER);
  DecimalFormat ratioFormat = new DecimalFormat("#.####");
  private final PrintWriter writer;

  /**
   * Constructs a MethylationCallWriter for the provided OutputStream.
   *
   * @param outputStream  the OutputStream to which to write the methylation calls
   */
  public MethylationCallWriter(OutputStream outputStream) {
    this.writer = new PrintWriter(outputStream);
  }

  /**
   * Closes this writer flushing anything in the buffer to the output stream.
   */
  @Override
  public void close() { writer.close(); }

  /**
   * Writes the provided MethylationCall to the underlying writer.
   *
   * @param methylationCall the MethylationCall to write
   */
  public void write(MethylationCall methylationCall) {
    String line;
    if (methylationCall.ratio.isPresent()) {
      line = joiner.join(
        methylationCall.contig,
        methylationCall.position,
        methylationCall.strand,
        methylationCall.methylatedCount,
        methylationCall.totalCount,
        ratioFormat.format(methylationCall.ratio.get())
      );
    } else {
      line = joiner.join(
        methylationCall.contig,
        methylationCall.position,
        methylationCall.strand,
        methylationCall.methylatedCount,
        methylationCall.totalCount
      );
    }
    writer.println(line);
  }

  /**
   * Writes the Iterable<MethylationCall> to the underlying writer.
   *
   * @param methylationCalls  the Iterable<MethylationCall> to write
   */
  public void write(Iterable<MethylationCall> methylationCalls) {
    for (MethylationCall methylationCall: methylationCalls) write(methylationCall);
  }

  /**
   * Writes the LinkedHashMap<String, Integer> specifying the sequence order and lengths
   *
   * @param sequenceDictionary  the LinkedHashMap<String, Integer> dictionary of sequences
   */
  public void writeHeader(SequenceDictionary sequenceDictionary) {
    writer.println(FORMAT_IDENTIFIER);

    // Write the control contigs
    for (String contig : sequenceDictionary.controlContigs) {
      String line = joiner.join(CONTROL, contig);
      writer.println(line);
    }

    for (String contig : sequenceDictionary.getSortedContigs()) {
      String line = joiner.join(SEQUENCE, contig, sequenceDictionary.contigLengthMap.get(contig));
      writer.println(line);
    }

    // Write the column headers
    String line = joiner.join("#Contig", "Position", "Strand", "Methylated", "Total", "Ratio");
    writer.println(line);
  }
}
