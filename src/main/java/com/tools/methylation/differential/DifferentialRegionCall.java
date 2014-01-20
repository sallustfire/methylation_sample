package com.tools.methylation.differential;

import org.apache.commons.lang3.builder.ToStringBuilder;

class DifferentialRegionCall extends DifferentialCall {
  public final String id;
  public final int start;
  public final int stop;

  public DifferentialRegionCall(String id,
                                String contig,
                                int start,
                                int stop,
                                double sample1Mean,
                                double sample2Mean,
                                double tStatistic,
                                double pValue) {
    super(contig, sample1Mean, sample2Mean, tStatistic, pValue);
    this.id = id;
    this.start = start;
    this.stop = stop;
  }

  @Override
  public boolean equals(Object other) {
    boolean result = false;
    if (other instanceof DifferentialRegionCall) {
      DifferentialRegionCall that = (DifferentialRegionCall) other;
      result = super.equals(other) && this.start == that.start && this.stop == that.stop && this.id.equals(that.id);
    }

    return result;
  }

  @Override
  public int hashCode() {
    return 41 * (41 * (41 * super.hashCode() + id.hashCode()) + start) + stop;
  }

  @Override
  public String toString() {
    ToStringBuilder stringBuilder = new ToStringBuilder(DifferentialSiteCall.class);
    stringBuilder.append("id", id);
    stringBuilder.append("start", start);
    stringBuilder.append("stop", stop);
    stringBuilder.appendSuper(super.toString());

    return stringBuilder.toString();
  }
}
