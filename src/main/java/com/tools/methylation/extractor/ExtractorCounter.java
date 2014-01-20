package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractWorker;
import net.sf.samtools.Cigar;
import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMRecord;

import java.util.ArrayDeque;

class ExtractorCounter extends AbstractWorker<Messages.AlignedReadsRead, Messages.MethylationCalculated> {
  private static final String GENOME_CONVERSION_TAG = "XG";
  private static final String METHYLATION_TAG = "XM";

  private final CoordinateConverter coordinateConverter;

  public ExtractorCounter(CoordinateConverter coordinateConverter, ActorRef writerRef) {
    super(writerRef);
    this.coordinateConverter = coordinateConverter;
  }

  @Override protected Class<Messages.AlignedReadsRead> getWorkClass() { return Messages.AlignedReadsRead.class; }

  @Override
  protected Messages.MethylationCalculated work(Messages.AlignedReadsRead message) {
    MethylationCounts methylationCounts = count(message.fragments);

    return new Messages.MethylationCalculated(message.index, methylationCounts);
  }

  private MethylationCounts count(ArrayDeque<AlignedFragment> fragments) {
    MethylationCounts counts = new MethylationCounts(coordinateConverter);

    for (AlignedFragment fragment : fragments) {
      if (fragment instanceof SingleEndFragment) {
        SingleEndFragment singleEndFragment = (SingleEndFragment) fragment;
        countSingle(counts, singleEndFragment.read);
      } else if (fragment instanceof PairedEndFragment) {
        PairedEndFragment pairedEndFragment = (PairedEndFragment) fragment;
        countPairedEnd(counts, pairedEndFragment.read1, pairedEndFragment.read2);
      }
    }

    return counts;
  }

  /**
   * Parses the methylation call string in conjunction with the Cigar sequence adding counts to this object's
   * cumulative counter.
   *
   * For paired end reads with overlapping sequence, the methylation calls are redundant, but can be omitted by
   * specifying the discard offset corresponding the number of reference positions for which calls should
   * be discarded before recognizing counts.
   *
   * @param contig            the String name of the contig on which the calls were made
   * @param startPosition     the int start position of the first call in the methylation call string
   * @param cigar             the Cigar sequence giving the mapping between the methylation calls and the reference
   * @param methylationCalls  the String with the bismark methylation calls
   * @param isForward         a boolean indicating if the calls correspond to the forward strand of the reference
   * @param discardOffset     the int number of reference positions to discard from the front of the call sequence
   */
  private void countCalls(MethylationCounts counts,
                          String contig,
                          int startPosition,
                          Cigar cigar,
                          String methylationCalls,
                          boolean isForward,
                          int discardOffset) {
    // The position at which calls all following calls can be recorded
    int callStartPosition = startPosition + discardOffset;

    // Iterate through the cigar chunks in parallel with the calls
    int callOffset = 0;
    for (CigarElement cigarElement : cigar.getCigarElements()) {
      CigarOperator operator = cigarElement.getOperator();
      if (CigarOperator.MATCH_OR_MISMATCH == operator) {
        for (int i = 0, n = cigarElement.getLength(); i < n; i++) {
          // Get the call and ensure that it is a methylation call
          char call = methylationCalls.charAt(callOffset);
          if (call != '.') {
            // Determine the position and count it if it is in range
            int position = i + startPosition;
            if (position >= callStartPosition) counts.count(call, isForward, contig, position);
          }

          // Update the index into the methylation call string
          callOffset += 1;
        }

        startPosition += cigarElement.getLength();
      } else if (CigarOperator.INSERTION == operator) {
        // Skip insertions
        for (int i = 0; i < cigarElement.getLength(); i++) callOffset += 1;
      } else if (CigarOperator.DELETION == operator) {
        // Incorporate deletions into the offset
        startPosition += cigarElement.getLength();
      } else {
        throw new RuntimeException("unrecognized operation encountered (" + operator + ")");
      }
    }
  }

  private void countPairedEnd(MethylationCounts counts, SAMRecord record1, SAMRecord record2) {
    // Determine the orientation of the fragment
    boolean isForward = record1.getAttribute(GENOME_CONVERSION_TAG).equals("CT");
    String contig = record1.getReferenceName();

    // Count the first read
    Cigar cigar1 = record1.getCigar();
    String methylationCalls1 = (String) record1.getAttribute(METHYLATION_TAG);
    countCalls(counts, contig, record1.getAlignmentStart(), cigar1, methylationCalls1, isForward, 0);

    // Count the second read dropping duplicate positions
    Cigar cigar2 = record2.getCigar();
    String methylationCalls2 = (String) record2.getAttribute(METHYLATION_TAG);
    int overlap = Math.max(0, record1.getAlignmentEnd() - record2.getAlignmentStart() + 1);
    countCalls(counts, contig, record2.getAlignmentStart(), cigar2, methylationCalls2, isForward, overlap);
  }

  private void countSingle(MethylationCounts counts, SAMRecord record) {
    // Extract variables
    boolean isForward = record.getAttribute(GENOME_CONVERSION_TAG).equals("CT");
    Cigar cigar = record.getCigar();
    String methylationCalls = (String) record.getAttribute(METHYLATION_TAG);

    // Count without an offset
    countCalls(counts, record.getReferenceName(), record.getAlignmentStart(), cigar, methylationCalls, isForward, 0);
  }

  public static Props props(final CoordinateConverter coordinateConverter, final ActorRef receiverRef) {
    return Props.create(new Creator<ExtractorCounter>() {
      @Override
      public ExtractorCounter create() throws Exception {
        return new ExtractorCounter(coordinateConverter, receiverRef);
      }
    });
  }
}
