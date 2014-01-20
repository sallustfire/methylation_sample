package com.tools.methylation.extractor;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.tools.io.SequenceDictionary;
import net.sf.samtools.SAMSequenceRecord;

import java.util.*;

/**
 * Provides a mapping between the reference contig based coordinate system and a single linear system that is easily
 * blocked.
 */
class CoordinateConverter {
  public final String stopContig;
  public final int stopPosition;

  private final List<ReferenceSequence> referenceSequences;
  private final HashMap<String, Long> sequenceOffsets;

  public CoordinateConverter(List<ReferenceSequence> referenceSequences, int offset)  {
    this(
      referenceSequences,
      offset,
      referenceSequences.get(referenceSequences.size() - 1).contig,
      referenceSequences.get(referenceSequences.size() - 1).length
    );
  }

  public CoordinateConverter(List<ReferenceSequence> referenceSequences,
                             int offset,
                             String stopContig,
                             int stopPosition)  {
    if (referenceSequences.size() == 0) {
      throw new IllegalArgumentException("must include at least 1 reference sequence for offset: " +
        offset + ", stop: " + stopContig + ":" + stopPosition);
    }

    this.referenceSequences = referenceSequences;
    this.stopContig = stopContig;
    this.stopPosition = stopPosition;

    // Create a map that gives the offsets for all of the sequences
    this.sequenceOffsets = new HashMap<>();
    long cumulativeLength = -1 * offset;
    for (ReferenceSequence referenceSequence : referenceSequences) {
      sequenceOffsets.put(referenceSequence.contig, cumulativeLength);
      cumulativeLength += referenceSequence.length;
    }

    // Ensure that the stop position is meaningful
    ReferenceSequence lastSequence = referenceSequences.get(referenceSequences.size() - 1);
    if (!lastSequence.contig.equals(stopContig)) {
      throw new IllegalArgumentException("last contig " + lastSequence.contig + " does not match stop " + stopContig);
    }
  }

  @Override
  public boolean equals(Object other) {
    boolean result = false;
    if (other instanceof CoordinateConverter) {
      CoordinateConverter that = (CoordinateConverter) other;
      result = this.referenceSequences.equals(that.referenceSequences)
        && this.sequenceOffsets.equals(that.sequenceOffsets);
    }

    return result;
  }

  @Override
  public int hashCode() {
    return 41 * referenceSequences.hashCode() + sequenceOffsets.hashCode();
  }

  public long convert(String sequenceName, int sequencePosition) {
    long offset = sequenceOffsets.get(sequenceName);
    return offset + sequencePosition;
  }

  public InverseCoordinateConvertor inverseConvertor() {
    return new InverseCoordinateConvertor(referenceSequences, sequenceOffsets, stopContig, stopPosition);
  }

  public long referenceLength() {
    long length = sequenceOffsets.get(referenceSequences.get(0).contig);
    for (ReferenceSequence referenceSequence : referenceSequences) length += referenceSequence.length;

    // Trim the reference sequence stop
    ReferenceSequence lastSequence = referenceSequences.get(referenceSequences.size() - 1);
    length -= lastSequence.length - stopPosition;

    return length;
  }

  /**
   * Splits this Coordinate Convertor into smaller chunks providing coordinate mappings against sub-regions of the
   * genome.
   *
   * @param chunkSize the int number of bases to include in each chunk
   */
  public ArrayList<CoordinateConverter> split(int chunkSize) {
    int chunkCount = (int) Math.ceil((double) referenceLength() / chunkSize);
    InverseCoordinateConvertor inverseCoordinateConvertor = inverseConvertor();

    ArrayList<CoordinateConverter> coordinateConverters = new ArrayList<>(chunkCount);
    PeekingIterator<ReferenceSequence> sequenceIterator = Iterators.peekingIterator(referenceSequences.iterator());
    int offset = 0;
    for (int i = 0; i < chunkCount; i++) {
      long chunkStop = (i + 1) * (long) chunkSize;

      ArrayList<ReferenceSequence> referenceSequences = new ArrayList<>();
      int cumulativeLength = -1 * offset;
      while (sequenceIterator.hasNext() &&
             sequenceOffsets.get(sequenceIterator.peek().contig) + sequenceIterator.peek().length < chunkStop) {
        cumulativeLength += sequenceIterator.peek().length;
        referenceSequences.add(sequenceIterator.next());
      }

      // Check if the next contig is split
      if (sequenceIterator.hasNext() && sequenceOffsets.get(sequenceIterator.peek().contig) < chunkStop) {
        referenceSequences.add(sequenceIterator.peek());
      }

      InverseCoordinateConvertor.ContigPosition stopPosition = inverseCoordinateConvertor.convert(chunkStop);
      CoordinateConverter chunkConverter = new CoordinateConverter(
        referenceSequences,
        offset,
        stopPosition.contig,
        stopPosition.position
      );
      coordinateConverters.add(chunkConverter);

      // Update the offset
      offset = chunkSize - cumulativeLength;
    }

    return coordinateConverters;
  }

  /**
   * Constructs and returns a CoordinateConverter using the coordinates defined by the provided SequenceDictionary.
   * The resultant coordinate order is taken to be the order of the provided records with any control contigs inserted
   * at the beginning, where the control contigs are assume to be biologically absent of methylation.
   *
   * @param sequenceDictionary the SequenceDictionary from which to build the CoordinateConverter
   * @return a CoordinateConverter mapping the reference coordinates to a linear scale
   */
  public static CoordinateConverter fromSequenceDictionary(SequenceDictionary sequenceDictionary) {
    ArrayList<ReferenceSequence> referenceSequences = new ArrayList<>();
    for (String contig : sequenceDictionary.getSortedContigs()) {
      int length = sequenceDictionary.contigLengthMap.get(contig);
      ReferenceSequence referenceSequence = new ReferenceSequence(contig, length);

      referenceSequences.add(referenceSequence);
    }
    
    return new CoordinateConverter(referenceSequences, 0);
  }

  public static class ReferenceSequence {
    public String contig;
    public int length;

    public ReferenceSequence(String contig, int length) {
      this.contig = contig;
      this.length = length;
    }

    @Override
    public boolean equals(Object other) {
      boolean result = false;
      if (other instanceof ReferenceSequence) {
        ReferenceSequence that = (ReferenceSequence) other;
        result = this.contig.equals(that.contig) && this.length == that.length;
      }

      return result;
    }

    @Override
    public int hashCode() {
      return 41 * contig.hashCode() + length;
    }
  }
}
