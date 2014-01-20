package com.tools.methylation.extractor;

class DepthCounter {
  public long cumulativeDepth;
  public long cumulativeMethylated;
  public long siteCount;

  public DepthCounter() {
    this.cumulativeDepth = 0;
    this.siteCount = 0;
  }

  public void count(int methylationCount, int siteDepth) {
    cumulativeDepth += siteDepth;
    cumulativeMethylated += methylationCount;
    siteCount += 1;
  }

  public double meanDepth() { return (double) cumulativeDepth / siteCount; }
  public double methylationRate() { return (double) cumulativeMethylated / cumulativeDepth; }

  public void merge(DepthCounter counter) {
    this.cumulativeDepth += counter.cumulativeDepth;
    this.cumulativeMethylated += counter.cumulativeMethylated;
    this.siteCount += counter.siteCount;
  }
}
