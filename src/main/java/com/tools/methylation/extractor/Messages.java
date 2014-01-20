package com.tools.methylation.extractor;

import com.tools.actors.AbstractMessages;
import com.tools.io.MethylationCall;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;

class Messages extends AbstractMessages {
  // Messages that a block has been created
  public static class BlockingCompleted { }

  // Messages that the counts for the block were written out
  public static class BlockCountsWritten {
    public final Map<Character, ArrayList<MethylationCall>> remainders;
    public final Map<String, DepthCounter> depthCounts;

    public BlockCountsWritten(Map<Character, ArrayList<MethylationCall>> remainders,
                              Map<String, DepthCounter> depthCounts) {
      this.remainders = remainders;
      this.depthCounts = depthCounts;
    }
  }

  // Messages that a block has been written
  public static class BlocksWritten {
    public final ArrayList<BlockedReads> blockedReads;

    public BlocksWritten(ArrayList<BlockedReads> blockedReads) {
      this.blockedReads = blockedReads;
    }
  }

  // Messages that a group of reads have been read
  public static class AlignedReadsRead extends AbstractMessages.Work {
    public final ArrayDeque<AlignedFragment> fragments;

    public AlignedReadsRead(int index, ArrayDeque<AlignedFragment> fragments) {
      super(index);
      this.fragments = fragments;
    }
  }

  // Messages that a block of reads have been converted to methylation counts
  public static class MethylationCalculated extends AbstractMessages.WorkComplete {
    public final MethylationCounts counts;

    public MethylationCalculated(int index, MethylationCounts counts) {
      super(index);
      this.counts = counts;
    }
  }

  // Messages that a block of reads have been converted to methylation counts
  public static class ReadsBlocked extends AbstractMessages.WorkComplete {
    public final ArrayList<BlockerWorker.ReadBlock> blocks;

    public ReadsBlocked(int index, ArrayList<BlockerWorker.ReadBlock> blocks) {
      super(index);
      this.blocks = blocks;
    }
  }

  public static class WriteAll {}
  public static class WriteAllComplete {
    public final Map<Character, ArrayList<MethylationCall>> remainders;
    public final Map<String, DepthCounter> depthCounts;

    public WriteAllComplete(Map<Character, ArrayList<MethylationCall>> remainders,
                            Map<String, DepthCounter> depthCounts) {
      this.remainders = remainders;
      this.depthCounts = depthCounts;
    }
  }
}
