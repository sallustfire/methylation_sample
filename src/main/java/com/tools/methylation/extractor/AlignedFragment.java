package com.tools.methylation.extractor;

abstract class AlignedFragment {
  public abstract String contig();
  public abstract int start();
  public abstract int stop();
}
