package com.tools.methylation.merger;

import com.tools.actors.AbstractMessages;
import com.tools.io.MethylationCall;

import java.util.ArrayDeque;
import java.util.ArrayList;

class Messages extends AbstractMessages {

  // Messages that a collection of co-located MethylationCalls can be merged
  public static class Work extends AbstractMessages.Work {
    public final ArrayList<ArrayDeque<MethylationCall>> mergeableBlocks;

    public Work(int index, ArrayList<ArrayDeque<MethylationCall>> mergeableBlocks) {
      super(index);
      this.mergeableBlocks = mergeableBlocks;
    }
  }

  // Messages that a block has been merged and is ready to be written
  public static class MergeComplete extends AbstractMessages.WorkComplete {
    public final ArrayList<MethylationCall> methylationCalls;

    public MergeComplete(int index, ArrayList<MethylationCall> methylationCalls) {
      super(index);
      this.methylationCalls = methylationCalls;
    }
  }
}
