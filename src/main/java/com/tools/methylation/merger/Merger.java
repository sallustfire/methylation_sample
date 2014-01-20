package com.tools.methylation.merger;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractWorker;
import com.tools.io.MethylationCall;
import com.tools.io.SequenceDictionary;

import java.util.*;

/**
 * Merges co-located methylation counts.
 */
class Merger extends AbstractWorker<Messages.Work, Messages.MergeComplete> {
  private final SequenceDictionary sequenceDictionary;

  public Merger(SequenceDictionary sequenceDictionary, ActorRef receiverRef) {
    super(receiverRef);

    this.sequenceDictionary = sequenceDictionary;
  }

  @Override protected Class<Messages.Work> getWorkClass() { return Messages.Work.class; }

  @Override
  protected Messages.MergeComplete work(Messages.Work work) {
    ArrayList<MethylationCall> mergedCalls = merge(work.mergeableBlocks, sequenceDictionary.contigOrderMap);

    return new Messages.MergeComplete(work.index, mergedCalls);
  }

  /**
   * Returns an ArrayList of MethylationCalls built out of merging co-located counts across all the blocks.
   *
   * @param callBlocks    the List<ArrayDeque<MethylationCall>> enumerating the calls across all of the blocks
   * @param contigOrder   the HashMap<String, Integer> specifying the order of the contigs
   *
   * @return an ArrayList<MethylationCall> with the merged calls in the contig order
   */
  protected ArrayList<MethylationCall> merge(List<ArrayDeque<MethylationCall>> callBlocks,
                                             final HashMap<String, Integer> contigOrder) {
    HashMap<String, TreeMap<Integer, MethylationCount>> mergedCalls = new HashMap<>();
    for (ArrayDeque<MethylationCall> callBlock : callBlocks) {
      for (MethylationCall call : callBlock) {
        // Retrieve the contig call count structure
        TreeMap<Integer, MethylationCount> contigCounts = mergedCalls.get(call.contig);
        if (contigCounts == null) {
          contigCounts = new TreeMap<>();
          mergedCalls.put(call.contig, contigCounts);
        }

        // Increment the counts
        MethylationCount count = contigCounts.get(call.position);
        if (count == null) {
          count = new MethylationCount(call.strand);
          contigCounts.put(call.position, count);
        }
        count.count(call.methylatedCount, call.totalCount);
      }
    }

    // Order the counts using the contig order
    Comparator<String> contigComparator = new Comparator<String>() {
      @Override
      public int compare(String contig1, String contig2) {
        return contigOrder.get(contig1) - contigOrder.get(contig2) ;
      }
    };
    ArrayList<String> contigs = new ArrayList<>(mergedCalls.keySet());
    Collections.sort(contigs, contigComparator);

    ArrayList<MethylationCall> methylationCalls = new ArrayList<>();
    for (String contig : contigs) {
      for (Map.Entry<Integer, MethylationCount> countEntry : mergedCalls.get(contig).entrySet()) {
        MethylationCount count = countEntry.getValue();
        MethylationCall mergedCall = new MethylationCall(
          contig,
          countEntry.getKey(),
          count.strand,
          count.methylatedCount,
          count.totalCount
        );
        methylationCalls.add(mergedCall);
      }
    }

    return methylationCalls;
  }

  public static Props props(final SequenceDictionary sequenceDictionary, final ActorRef receiverRef) {
    return Props.create(new Creator<Merger>() {
      @Override
      public Merger create() throws Exception {
        return new Merger(sequenceDictionary, receiverRef);
      }
    });
  }

  class MethylationCount {
    private char strand;
    private int methylatedCount;
    private int totalCount;

    public MethylationCount(char strand) {
      this.strand = strand;
      this.methylatedCount = 0;
      this.totalCount = 0;
    }

    public void count(int methylatedCount, int totalCount) {
      this.methylatedCount += methylatedCount;
      this.totalCount += totalCount;
    }
  }
}
