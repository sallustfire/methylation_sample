package com.tools.methylation.differential;

public class DifferentialCallMissing extends DifferentialCall {
  public final String id;
  public final int start;
  public final int stop;

  public DifferentialCallMissing(String id,
                                 String contig,
                                 int start,
                                 int stop) {
    super(contig, 0, 0, 0, 0);
    this.id = id;
    this.start = start;
    this.stop = stop;
  }
}
