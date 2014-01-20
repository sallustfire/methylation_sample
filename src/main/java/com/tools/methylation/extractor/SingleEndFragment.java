package com.tools.methylation.extractor;

import net.sf.samtools.SAMRecord;

class SingleEndFragment extends AlignedFragment {
  public final SAMRecord read;

  public SingleEndFragment (SAMRecord read) {
    this.read = read;
  }

  @Override public String contig() { return read.getReferenceName(); }
  @Override public int start() { return read.getAlignmentStart(); }
  @Override public int stop() { return read.getAlignmentEnd(); }
}
