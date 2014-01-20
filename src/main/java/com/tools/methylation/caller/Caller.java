package com.tools.methylation.caller;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractWorker;
import com.tools.io.MethylationCall;

import java.util.*;

/**
 * Makes consensus methylation calls using one or more biological replicates.
 */
class Caller extends AbstractWorker<Messages.CallsRead, Messages.CallingComplete> {
  private final double errorRate;

  public Caller(double errorRate, ActorRef writerRef) {
    super(writerRef);
    this.errorRate = errorRate;
  }

  @Override protected Class<Messages.CallsRead> getWorkClass() { return Messages.CallsRead.class; }

  @Override
  protected Messages.CallingComplete work(Messages.CallsRead message) {
    ArrayList<MethylationCall> consensusCalls = callRatios(message.calls);
    return new Messages.CallingComplete(message.index, consensusCalls);
  }

  /**
   * Returns an ArrayList of ObservedRatios giving the error rate corrected methylation ratio at a given site.
   *
   * @param calls the ArrayDeque<MethylationCall> enumerating the ordered methylation calls
   *
   * @return an ArrayList<RatioCall> with the called ratios
   */
  protected ArrayList<MethylationCall> callRatios(ArrayDeque<MethylationCall> calls) {
    ArrayList<MethylationCall> ratioCalls = new ArrayList<>(calls.size());
    for (MethylationCall call : calls) {
      // Calculate the corrected ratio
      double rawRatio = (double) call.methylatedCount / call.totalCount;
      double ratio = Math.max(0, (rawRatio - errorRate) / (1 - errorRate));

      MethylationCall ratioCall = new MethylationCall(
        call.contig,
        call.position,
        call.strand,
        call.methylatedCount,
        call.totalCount,
        ratio
      );
      ratioCalls.add(ratioCall);
    }

    return ratioCalls;
  }

  public static Props props(final double errorRate, final ActorRef writerRef) {
    return Props.create(new Creator<Caller>() {
      @Override
      public Caller create() throws Exception {
        return new Caller(errorRate, writerRef);
      }
    });
  }
}
