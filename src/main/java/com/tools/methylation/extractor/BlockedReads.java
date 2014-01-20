package com.tools.methylation.extractor;

import net.sf.samtools.SAMFileReader;

import java.nio.file.Files;
import java.nio.file.Path;

class BlockedReads {
  public Path path;
  public CoordinateConverter coordinateConverter;

  public BlockedReads(Path path, CoordinateConverter coordinateConverter) {
    this.path = path;
    this.coordinateConverter = coordinateConverter;
  }

  public boolean isEmpty() {
    boolean isEmpty;
    if (Files.exists(path)) {
      try (SAMFileReader samReader = new SAMFileReader(path.toFile())) {
        isEmpty = !samReader.iterator().hasNext();
      }
    } else isEmpty = true;

    return isEmpty;
  }
}
