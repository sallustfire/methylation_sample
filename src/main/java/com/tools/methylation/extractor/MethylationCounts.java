package com.tools.methylation.extractor;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.tools.io.MethylationCall;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

// Counts methylation calls using relative positions
class MethylationCounts {
  // The context code symbols used by Bismark
  public static final char CPG_CONTEXT = 'Z';
  public static final char CHG_CONTEXT = 'X';
  public static final char CHH_CONTEXT = 'H';
  public static final char CN_CHN_CONTEXT = 'U';

  // These bits identify the positions that store additional information about the calls at a position
  private static final int METHYLATED_BIT = 0x1;
  private static final int REVERSE_STRAND_BIT = 0x2;

  public ArrayList<TreeMap<Integer, Integer>> callCounters;

  private final CoordinateConverter coordinateConverter;
  private final long stop;

  public MethylationCounts(CoordinateConverter coordinateConverter) {
    this.coordinateConverter = coordinateConverter;
    this.stop = coordinateConverter.convert(coordinateConverter.stopContig, coordinateConverter.stopPosition);

    this.callCounters = new ArrayList<>();
    for (int i = 0; i < 4; i++) this.callCounters.add(new TreeMap<Integer, Integer>());
  }

  public void count(char call, boolean isForward, String contig, int position) {
    int adjustedPosition = getIndex(contig, position, isForward, Character.isUpperCase(call));

    // Calculate the index for the count category
    int index = contextIndex(call);

    // Update the count
    TreeMap<Integer, Integer> callCounter = callCounters.get(index);
    Integer count = callCounter.get(adjustedPosition);
    int adjustedCount = count == null ? 0 : count;
    callCounter.put(adjustedPosition, adjustedCount + 1);
  }

  /**
   * Merges all of the counts from the provided counter into this.
   *
   * @param counts the CallCounter with counts to add to this counter
   */
  public void countAll(MethylationCounts counts) {
    // Merge all of the counts
    for (int i = 0; i < counts.callCounters.size(); i++ ) {
      TreeMap<Integer, Integer> callCounter = callCounters.get(i);
      for (Map.Entry<Integer, Integer> callCount : counts.callCounters.get(i).entrySet()) {
        Integer count = callCounter.get(callCount.getKey());
        int adjustedCount = count == null ? 0 : count;
        callCounter.put(callCount.getKey(), adjustedCount + callCount.getValue());
      }
    }
  }

  public void countAll(Map<Character, ArrayList<MethylationCall>> contextCounts) {
    for (Map.Entry<Character, ArrayList<MethylationCall>> entryPair : contextCounts.entrySet()) {
      // Get the counter
      int index = contextIndex(entryPair.getKey());
      TreeMap<Integer, Integer> callCounter = callCounters.get(index);

      for (MethylationCall call : entryPair.getValue()) {
        boolean isForward = call.strand == '+';
        int methylatedCount = call.methylatedCount;
        int unmethylatedCount = call.totalCount - call.methylatedCount;

        if (methylatedCount > 0) {
          int adjustedPosition = getIndex(call.contig, call.position, isForward, true);
          Integer count = callCounter.get(adjustedPosition);
          int adjustedCount = count == null ? 0 : count;
          callCounter.put(adjustedPosition, adjustedCount + methylatedCount);
        }

        if (unmethylatedCount > 0) {
          if (methylatedCount > 0) {
            int adjustedPosition = getIndex(call.contig, call.position, isForward, false);
            Integer count = callCounter.get(adjustedPosition);
            int adjustedCount = count == null ? 0 : count;
            callCounter.put(adjustedPosition, adjustedCount + unmethylatedCount);
          }
        }
      }
    }
  }

  public MethylationCallIterator iterator(char context) {
    // Get the counts for the context
    int index = contextIndex(context);
    TreeMap<Integer, Integer> contextCounts = callCounters.get(index);

    // Construct an iterator

    return new MethylationCallIterator(contextCounts, coordinateConverter.inverseConvertor(), stop);
  }

  private int contextIndex(char context) {
    int index;
    switch(Character.toUpperCase(context)) {
      case CHH_CONTEXT:
        index = 0;
        break;
      case CHG_CONTEXT:
        index = 1;
        break;
      case CPG_CONTEXT:
        index = 2;
        break;
      case CN_CHN_CONTEXT:
        index = 3;
        break;
      default:
        throw new RuntimeException("unknown context encountered: " + context);
    }

    return index;
  }

  private int getIndex(String contig, int position, boolean isForward, boolean isMethylated) {
    // Flatten the position
    int flattenPosition = (int) coordinateConverter.convert(contig, position);

    // Adjust the position to the counter key
    int adjustedPosition = flattenPosition << 2;
    if (!isForward) adjustedPosition |= REVERSE_STRAND_BIT;
    if (isMethylated) adjustedPosition |= METHYLATED_BIT;

    return adjustedPosition;
  }

  public class MethylationCallIterator extends AbstractIterator<MethylationCall> {
    private final PeekingIterator<Map.Entry<Integer, Integer>> entries;
    private final InverseCoordinateConvertor inverseCoordinateConvertor;
    private final long stop;

    public MethylationCallIterator(TreeMap<Integer, Integer> callCounts,
                                   InverseCoordinateConvertor inverseCoordinateConvertor,
                                   long stop) {
      this.entries = Iterators.peekingIterator(callCounts.entrySet().iterator());
      this.inverseCoordinateConvertor = inverseCoordinateConvertor;
      this.stop = stop;
    }

    @Override
    public MethylationCall computeNext() {
      MethylationCall nextElement;
      if (entries.hasNext() && (entries.peek().getKey() >> 2) <= stop) nextElement = parseNextCall();
      else nextElement = endOfData();

      return nextElement;
    }

    public ArrayList<MethylationCall> getRemainingCounts() {
      ArrayList<MethylationCall> counts = new ArrayList<>();
      if (entries.hasNext()) counts.add(parseNextCall());

      return counts;
    }

    private MethylationCall parseNextCall() {
      Map.Entry<Integer, Integer> entryPair = entries.next();
      Integer key = entryPair.getKey();

      // Get the position, strand, and type
      int position = key >> 2;
      boolean isForward = (key & REVERSE_STRAND_BIT) == 0;
      boolean isMethylated = (key & METHYLATED_BIT) != 0;

      // Determine the counts checking the following entry if necessary
      int methylatedCount, unmethylatedCount;
      if (entries.hasNext() && entries.peek().getKey() == (key | METHYLATED_BIT)) {
        unmethylatedCount = entryPair.getValue();
        methylatedCount = entries.next().getValue();
      } else if (isMethylated) {
        methylatedCount = entryPair.getValue();
        unmethylatedCount = 0;
      } else {
        methylatedCount = 0;
        unmethylatedCount = entryPair.getValue();
      }

      // Get the reference position
      InverseCoordinateConvertor.ContigPosition contigPosition = inverseCoordinateConvertor.convert(position);
      char strand = isForward ? '+' : '-';
      return new MethylationCall(
        contigPosition.contig,
        contigPosition.position,
        strand,
        methylatedCount,
        methylatedCount + unmethylatedCount
      );
    }

  }
}
