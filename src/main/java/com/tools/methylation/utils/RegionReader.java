package com.tools.methylation.utils;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.tools.actors.AbstractMessages;
import com.tools.actors.AbstractReader;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;
import com.tools.io.SequenceDictionary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads common chunks from a collection of input files and signals that differential methylation detection can be
 * performed on these chunks.  This Reader additionally respects region boundaries such that calls belonging to the
 * same region are not separated.
 */
public class RegionReader extends AbstractReader {
  // Calls that have been read off of a reader, but are buffered in case regions overlap
  private final ArrayList<ArrayDeque<MethylationCall>> bufferedCalls;

  // The readers for each of the input files
  private final ArrayList<MethylationCallReader> callReaders;
  private final SequenceDictionary consensusDictionary;
  private final int maxWorkSize;

  // The explicit regions for which to read counts
  private final PeekingIterator<Region> regions;

  public RegionReader(List<Path> inputPaths,
                      List<Region> regions,
                      int maxWorkSize,
                      ActorRef workerRef) throws IOException {
    super(workerRef);
    this.maxWorkSize = maxWorkSize;

    // Open all of the files and read in the headers
    this.callReaders = new ArrayList<>();
    this.bufferedCalls = new ArrayList<>();
    for (Path inputPath : inputPaths) {
      callReaders.add(new MethylationCallReader(Files.newInputStream(inputPath)));
      bufferedCalls.add(new ArrayDeque<MethylationCall>());
    }

    // Build the consensus sequence dictionary
    this.consensusDictionary = buildConsensusDictionary(callReaders);

    // Sort the regions
    this.regions = getSortedRegions(regions, consensusDictionary);
  }

  @Override
  public void postStop() throws Exception {
    super.postStop();

    // Close any open file handles
    for (MethylationCallReader callReader : callReaders) callReader.close();
  }

  @Override
  protected boolean isComplete() {
    return !regions.hasNext();
  }

  @Override
  protected AbstractMessages.Work read(int blockIndex) {
    ArrayList<RegionCalls> extractedRegions = new ArrayList<>();

    // Successively read in calls for each of the regions
    int cumulativeCallCount = 0;
    while (regions.hasNext() && cumulativeCallCount < maxWorkSize) {
      Region region = regions.next();
      ArrayList<ArrayDeque<MethylationCall>> extractedCalls = new ArrayList<>();
      for (int i = 0; i < callReaders.size(); i++) {
        ArrayDeque<MethylationCall> calls = readRegionCalls(
          callReaders.get(i),
          bufferedCalls.get(i),
          region,
          consensusDictionary
        );
        extractedCalls.add(calls);

        cumulativeCallCount += calls.size();
      }

      extractedRegions.add(new RegionCalls(region, extractedCalls));
    }

    return new CallRegions(blockIndex, extractedRegions, consensusDictionary);
  }

  private SequenceDictionary buildConsensusDictionary(List<MethylationCallReader> callReaders) {
//    ArrayList<String> orderedContigs = new ArrayList<>();
//    for (MethylationCallReader callReader : callReaders) {
//      ArrayList<Integer> matchIndices = new ArrayList<>();
//      for (String contigName : callReader.getContigOrder().keySet()) {
//        matchIndices.add(orderedContigs.indexOf(contigName));
//      }
//
//      // Ensure that the indices are monotonically increasing
//      for (int i = 1; )
//    }

    return callReaders.get(0).sequenceDictionary;
  }

  private PeekingIterator<Region> getSortedRegions(List<Region> regions, final SequenceDictionary sequenceDictionary) {
    // Filter out the regions that aren't included
    ArrayList<Region> filteredRegions = new ArrayList<>();
    for (Region region : regions) {
      if (sequenceDictionary.contigOrderMap.containsKey(region.contig)) filteredRegions.add(region);
    }

    Comparator<Region> regionComparator = new Comparator<Region>() {
      @Override
      public int compare(Region region1, Region region2) {
        int contigComparison =
          sequenceDictionary.getContigIndex(region1.contig) - sequenceDictionary.getContigIndex(region2.contig);

        int result;
        if (contigComparison != 0) result = contigComparison;
        else result = region1.start - region2.start;

        return result;
      }
    };

    Collections.sort(filteredRegions, regionComparator);
    return Iterators.peekingIterator(filteredRegions.iterator());
  }

  /**
   * Returns a collection containing all of the calls falling inside the specified region.  Has the side effect of
   * updating the buffer.
   *
   * @param callReader          the MethylationCallReader from which to read
   * @param region              the Region for which to read calls
   * @param sequenceDictionary  the SequenceDictionary specifying the order of contigs
   */
  private ArrayDeque<MethylationCall> readRegionCalls(MethylationCallReader callReader,
                                                      ArrayDeque<MethylationCall> bufferedCalls,
                                                      Region region,
                                                      SequenceDictionary sequenceDictionary) {
    ArrayDeque<MethylationCall> calls = new ArrayDeque<>();

    // Stash the index of the contig for faster comparison
    int contigIndex = sequenceDictionary.getContigIndex(region.contig);

    // Search through the buffer for any stashed calls
    for (MethylationCall bufferedCall : bufferedCalls) {
      if (callPrecedes(bufferedCall, contigIndex, region.start - 1)) bufferedCalls.remove(bufferedCall);
      else if (callPrecedes(bufferedCall, contigIndex, region.stop)) calls.addLast(bufferedCall);
    }

    while (callReader.hasNext() && callPrecedes(callReader.peek(), contigIndex, region.stop)) {
      if (callPrecedes(callReader.peek(), contigIndex, region.start - 1)) callReader.next();
      else {
        bufferedCalls.addLast(callReader.peek());
        calls.addLast(callReader.next());
      }
    }

    return calls;
  }

  private boolean callPrecedes(MethylationCall methylationCall, int contigIndex, int position) {
    int callContigIndex = consensusDictionary.getContigIndex(methylationCall.contig);
    return (callContigIndex < contigIndex) || (callContigIndex == contigIndex && methylationCall.position <= position);
  }

  public static Props props(final List<Path> inputPaths,
                            final List<Region> regions,
                            final int maxWorkSize,
                            final ActorRef receiverRef) {
    return Props.create(new Creator<RegionReader>() {
      @Override
      public RegionReader create() throws Exception {
        return new RegionReader(inputPaths, regions, maxWorkSize, receiverRef);
      }
    });
  }

  public class RegionCalls {
    public final ArrayList<ArrayDeque<MethylationCall>> calls;
    public final Region region;

    public RegionCalls(Region region, ArrayList<ArrayDeque<MethylationCall>> calls) {
      this.region = region;
      this.calls = calls;
    }
  }
}
