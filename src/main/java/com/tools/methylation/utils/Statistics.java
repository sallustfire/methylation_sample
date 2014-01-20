package com.tools.methylation.utils;

import java.util.Collection;

public class Statistics {

  public static <T extends WeightedValue> double calculateMean(Collection<T> weightedValues) {
    double weightedSum = 0;
    int totalWeight = 0;
    for (WeightedValue weightedValue : weightedValues) {
      weightedSum += weightedValue.value * weightedValue.weight;
      totalWeight += weightedValue.weight;
    }

    return weightedSum / totalWeight;
  }

  public static <T extends WeightedValue> double calculateVariance(Collection<T> weightedValues, double mean) {
    double weightedSum = 0;
    int totalWeight = 0;
    int totalSquaredWeight = 0;
    for (WeightedValue weightedValue : weightedValues) {
      weightedSum += Math.pow(weightedValue.value - mean, 2) * weightedValue.weight ;
      totalWeight += weightedValue.weight;
      totalSquaredWeight += Math.pow(weightedValue.weight, 2);
    }

    double unbiasedCorrection = totalWeight / (Math.pow(totalWeight, 2) - totalSquaredWeight);
    return unbiasedCorrection * weightedSum;
  }

  public static class WeightedValue {
    public final double value;
    public final int weight;

    public WeightedValue(double value, int weight) {
      this.value = value;
      this.weight = weight;
    }
  }
}
