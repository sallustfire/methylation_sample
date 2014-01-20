package com.tools.methylation.caller;

import com.tools.actors.AbstractMessages;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;

import java.util.*;

class Messages extends AbstractMessages {

  // Messages that a collection of co-located MethylationCalls are ready to be processed
  public static class CallsRead extends Work {
    public final ArrayDeque<MethylationCall> calls;

    public CallsRead(int index, ArrayDeque<MethylationCall> calls) {
      super(index);
      this.calls = calls;
    }
  }

  // Messages that a block of calls have been generated
  public static class CallingComplete extends WorkComplete {
    public final Collection<MethylationCall> calls;

    public CallingComplete(int index, Collection<MethylationCall> calls) {
      super(index);
      this.calls = calls;
    }
  }
}
