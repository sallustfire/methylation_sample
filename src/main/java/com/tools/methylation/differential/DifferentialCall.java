package com.tools.methylation.differential;

import org.apache.commons.lang3.builder.ToStringBuilder;

public abstract class DifferentialCall {
  public final String contig;
  public final double sample1Mean;
  public final double sample2Mean;
  public final double tStatistic;
  public final double pValue;

  public DifferentialCall(String contig, double sample1Mean, double sample2Mean, double tStatistic, double pValue) {
    this.contig = contig;
    this.sample1Mean = sample1Mean;
    this.sample2Mean = sample2Mean;
    this.tStatistic = tStatistic;
    this.pValue = pValue;
  }

  @Override
  public boolean equals(Object other) {
    boolean result = false;
    if (other instanceof DifferentialSiteCall) {
      DifferentialSiteCall that = (DifferentialSiteCall) other;
      result = this.contig.equals(that.contig) &&
        Math.abs(this.sample1Mean - that.sample1Mean) < this.sample1Mean * 0.01 &&
        Math.abs(this.sample2Mean - that.sample2Mean) < this.sample2Mean * 0.01 &&
        Math.abs(this.tStatistic - that.tStatistic) < Math.abs(this.tStatistic) * 0.01 &&
        Math.abs(this.pValue - that.pValue) < this.pValue * 0.01;
    }

    return result;
  }

  @Override
  public int hashCode() {
    return 41 * (
      41 * (
        41 * (
          41 * contig.hashCode() + new Double(sample1Mean).hashCode()
        ) + new Double(sample2Mean).hashCode()
      ) + new Double(tStatistic).hashCode()
    ) + new Double(pValue).hashCode();
  }

  @Override
  public String toString() {
    ToStringBuilder stringBuilder = new ToStringBuilder(DifferentialSiteCall.class);
    stringBuilder.append("contig", contig);
    stringBuilder.append("sample1Mean", sample1Mean);
    stringBuilder.append("sample2Mean", sample2Mean);
    stringBuilder.append("tStatistic", tStatistic);
    stringBuilder.append("pValue", pValue);

    return stringBuilder.toString();
  }
}
