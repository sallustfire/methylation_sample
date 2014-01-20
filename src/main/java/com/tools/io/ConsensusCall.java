package com.tools.io;

/**
 * Represents a methylation call derived from on or more replicates.
 */
public class ConsensusCall {
  public final String contig;
  public final int position;
  public final char strand;
  public final double ratioMean;
  public final double ratioVariance;
  public final int sampleSize;
  public final int totalDepth;

  public ConsensusCall(String contig,
                       int position,
                       char strand,
                       double ratioMean,
                       double ratioVariance,
                       int sampleSize,
                       int totalDepth) {
    this.contig = contig;
    this.position = position;
    this.strand = strand;
    this.ratioMean = ratioMean;
    this.ratioVariance = ratioVariance;
    this.sampleSize = sampleSize;
    this.totalDepth = totalDepth;
  }
}
