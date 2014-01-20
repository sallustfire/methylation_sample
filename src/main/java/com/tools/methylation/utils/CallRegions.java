package com.tools.methylation.utils;

import com.tools.actors.AbstractMessages;
import com.tools.io.SequenceDictionary;

import java.util.ArrayList;

// Messages that a collection of co-located MethylationCalls can be merged
public class CallRegions extends AbstractMessages.Work {
  public final ArrayList<RegionReader.RegionCalls> regionCalls;
  public final SequenceDictionary sequenceDictionary;

  public CallRegions(int index,
                     ArrayList<RegionReader.RegionCalls> regionCalls,
                     SequenceDictionary sequenceDictionary) {
    super(index);
    this.regionCalls = regionCalls;
    this.sequenceDictionary = sequenceDictionary;
  }
}