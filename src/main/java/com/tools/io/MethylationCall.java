package com.tools.io;

import com.google.common.base.Optional;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * The cumulative methylation call at a single bp site.
 */
public class MethylationCall {
  public String contig;
  public int position;
  public char strand;
  public int methylatedCount;
  public int totalCount;
  public Optional<Double> ratio;

  public MethylationCall(String contig, int position, char strand, int methylatedCount, int totalCount) {
    this(contig, position, strand, methylatedCount, totalCount, Optional.<Double>absent());
  }

  public MethylationCall(String contig, int position, char strand, int methylatedCount, int totalCount, double ratio) {
    this(contig, position, strand, methylatedCount, totalCount, Optional.of(ratio));
  }

  public MethylationCall(String contig,
                         int position,
                         char strand,
                         int methylatedCount,
                         int totalCount,
                         Optional<Double> ratio) {
    this.contig = contig;
    this.position = position;
    this.strand = strand;
    this.methylatedCount = methylatedCount;
    this.totalCount = totalCount;
    this.ratio = ratio;
  }

  @Override
  public boolean equals(Object other) {
    boolean result = false;
    if (other instanceof MethylationCall) {
      MethylationCall that = (MethylationCall) other;
      result = that.canEqual(this) &&
        this.contig.equals(that.contig) &&
        this.position == that.position &&
        this.strand == that.strand &&
        this.methylatedCount == that.methylatedCount &&
        this.totalCount == that.totalCount &&
        this.ratio == that.ratio;
    }

    return result;
  }

  @Override
  public int hashCode() {
    return 41 * (
      41 * (41 * (41 * (41 * position + contig.hashCode()) + strand) + methylatedCount) + totalCount
    ) + ratio.hashCode();
  }

  public boolean canEqual(Object other) {
    return other instanceof MethylationCall;
  }

  @Override
  public String toString() {
    ToStringBuilder stringBuilder = new ToStringBuilder(MethylationCall.class);
    stringBuilder.append("contig", contig);
    stringBuilder.append("position", position);
    stringBuilder.append("strand", strand);
    stringBuilder.append("methylatedCount", methylatedCount);
    stringBuilder.append("totalCount", totalCount);
    if (ratio.isPresent()) stringBuilder.append("ratio", ratio);

    return stringBuilder.toString();
  }
}
