package com.tools.methylation.differential;

import com.google.common.base.Optional;
import com.tools.actors.AbstractMessages;
import com.tools.io.MethylationCall;
import com.tools.io.SequenceDictionary;
import com.tools.methylation.utils.RegionReader;

import java.util.ArrayDeque;
import java.util.ArrayList;

class Messages extends AbstractMessages {

  // Messages that a collection of co-located MethylationCalls can be merged
  public static class Call extends Work {
    public final ArrayList<ArrayDeque<MethylationCall>> blocks;
    public final SequenceDictionary sequenceDictionary;

    public Call(int index,
                ArrayList<ArrayDeque<MethylationCall>> blocks,
                SequenceDictionary sequenceDictionary) {
      super(index);
      this.blocks = blocks;
      this.sequenceDictionary = sequenceDictionary;
    }
  }

  // Messages that a block has been merged and is ready to be written
  public static class CallingComplete extends WorkComplete {
    public final ArrayList<DifferentialCall> differentialCalls;
    public final Optional<SequenceDictionary> sequenceDictionary;

    public CallingComplete(int index,
                           ArrayList<DifferentialCall> differentialCalls) {
      super(index);
      this.differentialCalls = differentialCalls;
      this.sequenceDictionary = Optional.absent();
    }

    public CallingComplete(int index,
                           ArrayList<DifferentialCall> differentialCalls,
                           SequenceDictionary sequenceDictionary) {
      super(index);
      this.differentialCalls = differentialCalls;
      this.sequenceDictionary = Optional.of(sequenceDictionary);
    }
  }
}
