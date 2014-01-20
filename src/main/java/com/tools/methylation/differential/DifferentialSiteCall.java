package com.tools.methylation.differential;

import org.apache.commons.lang3.builder.ToStringBuilder;

class DifferentialSiteCall extends DifferentialCall {
  public final int position;
  public final char strand;

  public DifferentialSiteCall(String contig,
                              int position,
                              char strand,
                              double sample1Mean,
                              double sample2Mean,
                              double tStatistic,
                              double pValue) {
    super(contig, sample1Mean, sample2Mean, tStatistic, pValue);
    this.position = position;
    this.strand = strand;
  }

  @Override
  public boolean equals(Object other) {
    boolean result = false;
    if (other instanceof DifferentialSiteCall) {
      DifferentialSiteCall that = (DifferentialSiteCall) other;
      result = super.equals(other) && this.position == that.position && this.strand == that.strand;
    }

    return result;
  }

  @Override
  public int hashCode() {
    return 41 * (41 * super.hashCode() + position) + strand;
  }

  @Override
  public String toString() {
    ToStringBuilder stringBuilder = new ToStringBuilder(DifferentialSiteCall.class);
    stringBuilder.append("position", position);
    stringBuilder.append("strand", strand);
    stringBuilder.appendSuper(super.toString());

    return stringBuilder.toString();
  }
}
