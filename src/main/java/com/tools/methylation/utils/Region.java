package com.tools.methylation.utils;

import com.google.common.base.Optional;

/**
 * A identifiable genomic region.
 */
public class Region {
  public final String id;
  public final String contig;
  public final int start;
  public final int stop;
  public final Optional<Character> strand;

  public Region(String id, String contig, int start, int stop) {
    this.id = id;
    this.contig = contig;
    this.start = start;
    this.stop = stop;
    this.strand = Optional.absent();
  }

  public Region(String id, String contig, int start, int stop, char strand) {
    this.id = id;
    this.contig = contig;
    this.start = start;
    this.stop = stop;
    this.strand = Optional.of(strand);
  }

  public int length() { return stop - start + 1; }
}
