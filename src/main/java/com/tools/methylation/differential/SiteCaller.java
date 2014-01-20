package com.tools.methylation.differential;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.tools.io.MethylationCall;

import java.util.*;

class SiteCaller extends Caller {
  private final ArrayList<Integer> conditions;
  private final ActorRef receiverRef;

  public SiteCaller(List<Integer> conditions, ActorRef receiverRef) {
    this.conditions = new ArrayList<>(conditions);
    this.receiverRef = receiverRef;
  }

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof Messages.Call) {
      // Perform the calling
      Messages.Call call = (Messages.Call) message;
      ArrayList<DifferentialCall> differentialCalls = call(
        call.blocks,
        conditions,
        call.sequenceDictionary.contigOrderMap
      );

      // Message that the calling has been completed
      Messages.CallingComplete callingComplete = new Messages.CallingComplete(
        call.index,
        differentialCalls,
        call.sequenceDictionary
      );
      receiverRef.tell(callingComplete, getSelf());
    } else unhandled(message);
  }

  /**
   * Returns an ArrayList of Observations built out of merging co-located counts across all the blocks.
   *
   * @param callBlocks    the List<ArrayDeque<MethylationCall>> enumerating the calls across all of the blocks
   * @param conditions    the ArrayList<Integer> giving the corresponding condition values for each of the blocks
   * @param contigOrder   the HashMap<String, Integer> specifying the order of the contigs
   *
   * @return an ArrayList<MethylationCall> with the merged calls in the contig order
   */
  protected ArrayList<DifferentialCall> call(List<ArrayDeque<MethylationCall>> callBlocks,
                                             ArrayList<Integer> conditions,
                                             final HashMap<String, Integer> contigOrder) {
    // Generate the observations for all of the sites
    HashMap<String, TreeMap<Integer, ObservedSite>> mergedCalls = mergeCalls(callBlocks, conditions, MIN_COVERAGE);

    // Get the contigs in order
    // Order the counts using the contig order
    Comparator<String> contigComparator = new Comparator<String>() {
      @Override
      public int compare(String contig1, String contig2) {
        return contigOrder.get(contig1) - contigOrder.get(contig2) ;
      }
    };
    ArrayList<String> contigs = new ArrayList<>(mergedCalls.keySet());
    Collections.sort(contigs, contigComparator);

    // Perform the differential calling
    ArrayList<DifferentialSiteCall> rawCalls = new ArrayList<>();
    for (String contig : contigs) {
      ArrayList<DifferentialSiteCall> contigCalls = callContigSites(contig, mergedCalls.get(contig));
      rawCalls.addAll(contigCalls);
    }

    // Handle the filtering and return
    ArrayList<DifferentialCall> differentialCalls = new ArrayList<>();
    for (DifferentialSiteCall differentialSiteCall : rawCalls) {
      if (differentialSiteCall.pValue <= CUTOFF) differentialCalls.add(differentialSiteCall);
    }

    return differentialCalls;
  }

  private ArrayList<DifferentialSiteCall> callContigSites(String contig, TreeMap<Integer, ObservedSite> contigSites) {
    ArrayList<DifferentialSiteCall> calls = new ArrayList<>();
    for (Map.Entry<Integer, ObservedSite> sitePair : contigSites.entrySet()) {
      ObservedSite observedSite = sitePair.getValue();
      Optional<TTestResult> tTestResult = differentialTTest(observedSite.sample1Values, observedSite.sample2Values);
      if (tTestResult.isPresent()) {
        DifferentialSiteCall differentialCall = new DifferentialSiteCall(
          contig,
          sitePair.getKey(),
          observedSite.strand,
          tTestResult.get().sample1Mean,
          tTestResult.get().sample2Mean,
          tTestResult.get().tStatistic,
          tTestResult.get().pValue
        );
        calls.add(differentialCall);
      }
    }

    return calls;
  }

  public static Props props(final List<Integer> conditions, final ActorRef receiverRef) {
    return Props.create(new Creator<SiteCaller>() {
      @Override
      public SiteCaller create() throws Exception {
        return new SiteCaller(conditions, receiverRef);
      }
    });
  }

  class DifferentialCalls {
    public final int totalCallCount;
    public final ArrayList<DifferentialSiteCall> calls;

    public DifferentialCalls(ArrayList<DifferentialSiteCall> unfilteredCalls, double cutoff) {
      this.totalCallCount = unfilteredCalls.size();

      this.calls = new ArrayList<>();
      for (DifferentialSiteCall call : unfilteredCalls) {
        if (call.pValue < cutoff) calls.add(call);
      }
    }
  }
}
