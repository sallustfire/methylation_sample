package com.tools.methylation.extractor;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class InverseCoordinateConvertor {
  private final PeekingIterator<ReferenceSequenceCoordinates> sequenceCoordinates;
  private final String stopContig;
  private final int stopPosition;

  public InverseCoordinateConvertor(List<CoordinateConverter.ReferenceSequence> referenceSequences,
                                    Map<String, Long> offsets,
                                    String stopContig,
                                    int stopPosition) {
    this.stopContig = stopContig;
    this.stopPosition = stopPosition;

    ArrayList<ReferenceSequenceCoordinates> referenceCoordinates = new ArrayList<>();
    for (CoordinateConverter.ReferenceSequence referenceSequence : referenceSequences) {
      ReferenceSequenceCoordinates coordinates = new ReferenceSequenceCoordinates(
        referenceSequence.contig,
        offsets.get(referenceSequence.contig),
        referenceSequence.length
      );

      referenceCoordinates.add(coordinates);
    }

    this.sequenceCoordinates = Iterators.peekingIterator(referenceCoordinates.iterator());
  }

  public ContigPosition convert(long position) {
    // Ensure that the current sequence is the first sequence beyond this position
    while (sequenceCoordinates.hasNext() && position > sequenceCoordinates.peek().stop) sequenceCoordinates.next();

    ContigPosition result;
    if (sequenceCoordinates.hasNext()) {
      // Identify the sequence
      String sequenceName = sequenceCoordinates.peek().name;
      int contigPosition = (int) (position - sequenceCoordinates.peek().offset);

      result = new ContigPosition(sequenceName, contigPosition);
    } else result = new ContigPosition(stopContig, stopPosition);

    return result;
  }

  class ContigPosition {
    public final String contig;
    public final int position;

    public ContigPosition(String contig, int position) {
      this.contig = contig;
      this.position = position;
    }
  }

  public class ReferenceSequenceCoordinates {
    public final String name;
    public final long offset;
    public final long stop;

    public ReferenceSequenceCoordinates(String name, long offset, long length) {
      this.name = name;
      this.offset = offset;
      this.stop = offset + length;
    }
  }
}
