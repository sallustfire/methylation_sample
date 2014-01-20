package com.tools.methylation.caller;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.tools.actors.AbstractReader;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;

import java.io.IOException;
import java.util.ArrayDeque;

/**
 * Reads common chunks from a collection of input files.
 */
class Reader extends AbstractReader<Messages.CallsRead> {
  private final MethylationCallReader callReader;
  private final int maxWorkSize;

  public Reader(MethylationCallReader callReader, int maxWorkSize, ActorRef workerRef) throws IOException {
    super(workerRef);
    this.maxWorkSize = maxWorkSize;
    this.callReader = callReader;
  }

  @Override
  public void postStop() throws Exception {
    callReader.close();
    super.postStop();
  }

  @Override protected boolean isComplete() { return !callReader.hasNext(); }

  @Override
  protected Messages.CallsRead read(int blockIndex) {
    // Read the maximum number of calls permitted by the block size
    ArrayDeque<MethylationCall> calls = readCalls(callReader, maxWorkSize);

    // Create the message
    return new Messages.CallsRead(blockIndex, calls);
  }

  private ArrayDeque<MethylationCall> readCalls(MethylationCallReader callReader, int maxCount) {
    ArrayDeque<MethylationCall> calls = new ArrayDeque<>();
    while (callReader.hasNext() && calls.size() < maxCount) calls.addLast(callReader.next());

    return calls;
  }

  public static Props props(final MethylationCallReader callReader,
                            final int maxWorkSize,
                            final ActorRef receiverRef) {
    return Props.create(new Creator<Reader>() {
      @Override
      public Reader create() throws Exception {
        return new Reader(callReader, maxWorkSize, receiverRef);
      }
    });
  }
}
