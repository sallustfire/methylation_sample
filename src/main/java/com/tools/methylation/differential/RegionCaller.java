package com.tools.methylation.differential;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.tools.io.MethylationCall;
import com.tools.methylation.utils.CallRegions;
import com.tools.methylation.utils.Region;
import com.tools.methylation.utils.RegionReader;
import com.tools.methylation.utils.Statistics;

import java.util.*;

class RegionCaller extends Caller {
  private final ArrayList<Integer> conditions;
  private final ActorRef receiverRef;

  public RegionCaller(List<Integer> conditions, ActorRef receiverRef) {
    this.conditions = new ArrayList<>(conditions);
    this.receiverRef = receiverRef;
  }

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof CallRegions) {
      // Perform the calling
      CallRegions call = (CallRegions) message;
      ArrayList<DifferentialCall> differentialCalls = call(call.regionCalls, conditions);

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
   * @param regionCalls   the List<RegionReader.RegionCalls> enumerating the calls across all of the blocks
   *                      for each region
   * @param conditions    the ArrayList<Integer> giving the corresponding condition values for each of the blocks
   *
   * @return an ArrayList<MethylationCall> with the merged calls in the contig order
   */
  protected ArrayList<DifferentialCall> call(List<RegionReader.RegionCalls> regionCalls,
                                             ArrayList<Integer> conditions) {
    ArrayList<DifferentialCall> differentialCalls = new ArrayList<>();
    for (RegionReader.RegionCalls calledRegion : regionCalls) {
      differentialCalls.add(callRegion(calledRegion.region, calledRegion.calls, conditions));
    }

    return differentialCalls;
  }

  protected DifferentialCall callRegion(Region region,
                                        ArrayList<ArrayDeque<MethylationCall>> calls,
                                        ArrayList<Integer> conditions) {
    // Merge all of the calls together across the region
    TreeMap<Integer, ObservedSite> mergedCalls = mergeCalls(calls, conditions, MIN_COVERAGE).get(region.contig);
    if (mergedCalls == null) mergedCalls = new TreeMap<>();

    // Perform differential calling pooling all of the calls
    ObservedRegion observedRegion = new ObservedRegion(region.start, region.stop);
    for (ObservedSite observedSite : mergedCalls.values()) observedRegion.addObservations(observedSite);
    Optional<TTestResult> tTestResult = differentialTTest(observedRegion.sample1Values, observedRegion.sample2Values);

    DifferentialCall differentialCall;
    if (tTestResult.isPresent()) {
      differentialCall = new DifferentialRegionCall(
        region.id,
        region.contig,
        region.start,
        region.stop,
        tTestResult.get().sample1Mean,
        tTestResult.get().sample2Mean,
        tTestResult.get().tStatistic,
        tTestResult.get().pValue
      );
    } else differentialCall = new DifferentialCallMissing(region.id, region.contig, region.start, region.stop);

    return differentialCall;
  }

  public static Props props(final List<Integer> conditions, final ActorRef receiverRef) {
    return Props.create(new Creator<RegionCaller>() {
      @Override
      public RegionCaller create() throws Exception {
        return new RegionCaller(conditions, receiverRef);
      }
    });
  }

  class ObservedRegion {
    public final int start;
    public final int stop;
    public final ArrayList<Statistics.WeightedValue> sample1Values;
    public final ArrayList<Statistics.WeightedValue> sample2Values;

    public ObservedRegion(int start, int stop) {
      this.start = start;
      this.stop = stop;
      this.sample1Values = new ArrayList<>();
      this.sample2Values = new ArrayList<>();
    }

    public void addObservations(ObservedSite observedSite) {
      if (observedSite.position > stop || observedSite.position < start) {
        throw new IllegalArgumentException("site is out of bounds");
      }

      sample1Values.addAll(observedSite.sample1Values);
      sample2Values.addAll(observedSite.sample2Values);
    }
  }
}
