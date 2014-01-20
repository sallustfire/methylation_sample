package com.tools.methylation.differential;

import akka.actor.UntypedActor;
import com.google.common.base.Optional;
import com.tools.io.MethylationCall;
import com.tools.methylation.utils.Statistics;
import org.apache.commons.math3.distribution.TDistribution;

import java.util.*;

public abstract class Caller extends UntypedActor {
  protected static double CUTOFF = 0.05;
  protected static int MIN_COVERAGE = 3;

  protected Optional<TTestResult> differentialTTest(ArrayList<Statistics.WeightedValue> sample1Values,
                                                    ArrayList<Statistics.WeightedValue> sample2Values) {
    Optional<TTestResult> result = Optional.absent();

    int sample1Size = sample1Values.size();
    int sample2Size = sample2Values.size();
    if (sample1Size > 1 && sample2Size > 1) {
      // Calculate the weighted means and variance
      double sample1Mean = Statistics.calculateMean(sample1Values);
      double sample2Mean = Statistics.calculateMean(sample2Values);
      double sample1Variance = Statistics.calculateVariance(sample1Values, sample1Mean);
      double sample2Variance = Statistics.calculateVariance(sample2Values, sample2Mean);

      // Calculate Welch's T statistic
      double pooledVariance = sample1Variance / sample1Size + sample2Variance / sample2Size;
      double tStatistic = (sample1Mean - sample2Mean) / Math.sqrt(pooledVariance);

      // Calculate the degrees of freedom
      double denominator = Math.pow(sample1Variance / sample1Size, 2) / (sample1Size - 1) +
        Math.pow(sample2Variance / sample2Size, 2) / (sample2Size - 1);
      double degreesOfFreedom = Math.pow(pooledVariance, 2) / denominator;

      // Calculate the two tailed p-value
      TDistribution distribution = new TDistribution(degreesOfFreedom);
      double pValue;
      if (tStatistic > 0) pValue = 2 * (1 - distribution.cumulativeProbability(tStatistic));
      else pValue = 2 * distribution.cumulativeProbability(tStatistic);

      TTestResult tTestResult = new TTestResult(
        sample1Mean,
        sample2Mean,
        tStatistic,
        pValue
      );
      result = Optional.of(tTestResult);
    }

    return result;
  }

  // Merges the calls by contig and position
  protected HashMap<String, TreeMap<Integer, ObservedSite>> mergeCalls(List<ArrayDeque<MethylationCall>> callBlocks,
                                                                       ArrayList<Integer> conditions,
                                                                       int minCoverage) {
    HashMap<String, TreeMap<Integer, ObservedSite>> mergedCalls = new HashMap<>();
    for (int i = 0; i < callBlocks.size(); i++) {
      ArrayDeque<MethylationCall> callBlock = callBlocks.get(i);
      for (MethylationCall call : callBlock) {
        // Retrieve the contig call count structure
        TreeMap<Integer, ObservedSite> contigSites = mergedCalls.get(call.contig);
        if (contigSites == null) {
          contigSites = new TreeMap<>();
          mergedCalls.put(call.contig, contigSites);
        }

        // Add the observation
        ObservedSite site = contigSites.get(call.position);
        if (site == null) {
          site = new ObservedSite(call.position, call.strand);
          contigSites.put(call.position, site);
        }

        // Only make observations if there is sufficient coverage
        if (call.totalCount >= minCoverage) {
          double methylationRatio = (double) call.methylatedCount / call.totalCount;
          site.addObservation(methylationRatio, call.totalCount, conditions.get(i));
        }
      }
    }

    return mergedCalls;
  }

  class ObservedSite {
    public final int position;
    public final char strand;
    public final ArrayList<Statistics.WeightedValue> sample1Values;
    public final ArrayList<Statistics.WeightedValue> sample2Values;

    public ObservedSite(int position, char strand) {
      this.position = position;
      this.strand = strand;
      this.sample1Values = new ArrayList<>();
      this.sample2Values = new ArrayList<>();
    }

    public void addObservation(double value, int weight, int condition) {
      Statistics.WeightedValue weightedValue = new Statistics.WeightedValue(value, weight);
      if (condition == 0) sample1Values.add(weightedValue);
      else sample2Values.add(weightedValue);
    }
  }

  class TTestResult {
    public final double sample1Mean;
    public final double sample2Mean;
    public final double tStatistic;
    public final double pValue;

    public TTestResult(double sample1Mean, double sample2Mean, double tStatistic, double pValue) {
      this.sample1Mean = sample1Mean;
      this.sample2Mean = sample2Mean;
      this.tStatistic = tStatistic;
      this.pValue =  pValue;
    }
  }
}
