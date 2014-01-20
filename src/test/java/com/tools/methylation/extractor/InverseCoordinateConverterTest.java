package com.tools.methylation.extractor;

import com.tools.ApplicationTest;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMSequenceDictionary;
import org.javatuples.Triplet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class InverseCoordinateConverterTest extends ApplicationTest {
  private SAMSequenceDictionary sequenceDictionary;

  @Before
  public void initializeDictionary() throws Exception {
    Path samPath = getFileResource("/methylation/single_end_athaliana_reads.fastq_bismark.sam");
    try (SAMFileReader samReader = new SAMFileReader(samPath.toFile())) {
      sequenceDictionary = samReader.getFileHeader().getSequenceDictionary();
    }
  }

  @Test
  public void testConvert() throws Exception {
    CoordinateConverter coordinateConverter = CoordinateConverterTest.buildConverter(sequenceDictionary);
    InverseCoordinateConvertor inverseCoordinateConvertor = coordinateConverter.inverseConvertor();

    List<Triplet<String, Integer, Integer>> expectations = Arrays.asList(
      Triplet.with("Chr4", 1, 1),
      Triplet.with("Chr4", 10000, 10000),
      Triplet.with("Chr4", 10000000, 10000000),
      Triplet.with("Chr4", 10000001, 10000001),
      Triplet.with("Chr1", 1, 18585057),
      Triplet.with("Chr5", 26975502, 119716252)
    );

    for (Triplet<String, Integer, Integer> expectation : expectations) {
      InverseCoordinateConvertor.ContigPosition result = inverseCoordinateConvertor.convert(expectation.getValue2());
      Assert.assertEquals(expectation.getValue0(), result.contig);
      Assert.assertEquals((int) expectation.getValue1(), result.position);
    }
  }

  @Test
  public void testConvertOffset() throws Exception {
    CoordinateConverter coordinateConverter = CoordinateConverterTest.buildSamOffsetConverter(
      sequenceDictionary,
      10000
    );
    InverseCoordinateConvertor inverseCoordinateConvertor = coordinateConverter.inverseConvertor();

    List<Triplet<String, Integer, Integer>> expectations = Arrays.asList(
      Triplet.with("Chr4", 10001, 1),
      Triplet.with("Chr4", 10000000, 9990000),
      Triplet.with("Chr4", 10000001, 9990001),
      Triplet.with("Chr1", 1, 18575057),
      Triplet.with("Chr5", 26975502, 119706252)
    );

    for (Triplet<String, Integer, Integer> expectation : expectations) {
      InverseCoordinateConvertor.ContigPosition result = inverseCoordinateConvertor.convert(expectation.getValue2());
      Assert.assertEquals(expectation.getValue0(), result.contig);
      Assert.assertEquals((int) expectation.getValue1(), result.position);
    }
  }
}
