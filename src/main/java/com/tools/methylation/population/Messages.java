package com.tools.methylation.population;

import com.tools.actors.AbstractMessages;
import com.tools.io.MethylationCall;
import com.tools.io.SequenceDictionary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;

public class Messages extends AbstractMessages {

  // Messages that a block of calls have been generated
  public static class CallingComplete extends WorkComplete {
    public final Collection<Caller.PopulationRatio> calls;

    public CallingComplete(int index, Collection<Caller.PopulationRatio> calls) {
      super(index);
      this.calls = calls;
    }
  }

  // Messages that a collection of co-located MethylationCalls can be merged
  public static class CallsRead extends Work {
    public final ArrayList<ArrayDeque<MethylationCall>> blocks;
    public final SequenceDictionary sequenceDictionary;

    public CallsRead(int index,
                     ArrayList<ArrayDeque<MethylationCall>> blocks,
                     SequenceDictionary sequenceDictionary) {
      super(index);
      this.blocks = blocks;
      this.sequenceDictionary = sequenceDictionary;
    }
  }

  public static class Read { }
  public static class ReadComplete { }
}


