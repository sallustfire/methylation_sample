package com.tools.methylation.extractor;

import net.sf.samtools.SAMRecord;

class PairedEndFragment extends AlignedFragment {
  public final SAMRecord read1;
  public final SAMRecord read2;

  public PairedEndFragment(SAMRecord read1, SAMRecord read2) {
    if (read1.getAlignmentStart() <= read2.getAlignmentStart()) {
      this.read1 = read1;
      this.read2 = read2;
    } else {
      this.read1 = read2;
      this.read2 = read1;
    }
  }

  @Override public String contig() { return read1.getReferenceName(); }
  @Override public int start() { return read1.getAlignmentStart(); }
  @Override public int stop() { return read2.getAlignmentEnd(); }
}
