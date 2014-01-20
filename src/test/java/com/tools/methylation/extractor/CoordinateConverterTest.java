package com.tools.methylation.extractor;

import com.tools.ApplicationTest;
import com.tools.io.SequenceDictionary;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMSequenceDictionary;
import net.sf.samtools.SAMSequenceRecord;
import org.javatuples.Triplet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class CoordinateConverterTest extends ApplicationTest {
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

    List<Triplet<String, Integer, Integer>> expectations = Arrays.asList(
      Triplet.with("Chr4", 1, 1),
      Triplet.with("Chr4", 10000, 10000),
      Triplet.with("Chr4", 10000000, 10000000),
      Triplet.with("Chr4", 10000001, 10000001),
      Triplet.with("Chr1", 1, 18585057),
      Triplet.with("Chr5", 26975502, 119716252)
    );

    for (Triplet<String, Integer, Integer> expectation : expectations) {
      int result = (int) coordinateConverter.convert(expectation.getValue0(), expectation.getValue1());
      int expectedResult = expectation.getValue2();
      Assert.assertEquals(expectedResult, result);
    }

  }

  @Test
  public void testConvertOffset() throws Exception {
    CoordinateConverter coordinateConverter = buildSamOffsetConverter(sequenceDictionary, 10000);

    List<Triplet<String, Integer, Integer>> expectations = Arrays.asList(
      Triplet.with("Chr4", 10001, 1),
      Triplet.with("Chr4", 10000000, 9990000),
      Triplet.with("Chr4", 10000001, 9990001),
      Triplet.with("Chr1", 1, 18575057),
      Triplet.with("Chr5", 26975502, 119706252)
    );

    for (Triplet<String, Integer, Integer> expectation : expectations) {
      int result = (int) coordinateConverter.convert(expectation.getValue0(), expectation.getValue1());
      int expectedResult = expectation.getValue2();
      Assert.assertEquals(expectedResult, result);
    }
  }

  @Test
  public void testConvertControl() throws Exception {
    List<String> controlContigs = Arrays.asList("gi|9626243|ref|NC_001416.1|");
    CoordinateConverter coordinateConverter = buildConverter(sequenceDictionary, controlContigs);

    List<Triplet<String, Integer, Integer>> expectations = Arrays.asList(
      Triplet.with("gi|9626243|ref|NC_001416.1|", 1, 1),
      Triplet.with("gi|9626243|ref|NC_001416.1|", 48502, 48502),
      Triplet.with("Chr4", 1, 48503)
    );

    for (Triplet<String, Integer, Integer> expectation : expectations) {
      int result = (int) coordinateConverter.convert(expectation.getValue0(), expectation.getValue1());
      int expectedResult = expectation.getValue2();
      Assert.assertEquals(expectedResult, result);
    }
  }

  @Test
  public void testReferenceLength() throws Exception {
    CoordinateConverter coordinateConverter = buildConverter(sequenceDictionary);

    long expectedLength = 0;
    for (SAMSequenceRecord referenceSequence : sequenceDictionary.getSequences()) {
      expectedLength += referenceSequence.getSequenceLength();
    }
    Assert.assertEquals(expectedLength, coordinateConverter.referenceLength());
  }

  @Test
  public void testReferenceLengthWithStop() throws Exception {
    List<CoordinateConverter.ReferenceSequence> referenceSequences = Arrays.asList(
      new CoordinateConverter.ReferenceSequence("Chr4", 18585056)
    );
    CoordinateConverter coordinateConverter = new CoordinateConverter(referenceSequences, 0, "Chr4", 18000000);

    Assert.assertEquals(18000000, coordinateConverter.referenceLength());
  }

  @Test
  public void testSplit() throws Exception {
    CoordinateConverter coordinateConverter = buildConverter(sequenceDictionary);

    ArrayList<CoordinateConverter> result = coordinateConverter.split(18000000);
    ArrayList<CoordinateConverter> expected = new ArrayList<>();
    expected.add(buildSamOffsetConverter(sequenceDictionary.getSequences().subList(0, 1), 0));
    expected.add(buildSamOffsetConverter(sequenceDictionary.getSequences().subList(0, 2), 18000000));
    expected.add(buildSamOffsetConverter(sequenceDictionary.getSequences().subList(1, 3), 17414944));

    Assert.assertEquals(expected, result.subList(0, 3));
    Assert.assertEquals(18000000, result.get(0).referenceLength());
  }

  public static CoordinateConverter buildConverter(SAMSequenceDictionary samSequenceDictionary,
                                                   List<String> controlContigs) {
    LinkedHashMap<String, Integer> contigLengths = new LinkedHashMap<>();
    for (SAMSequenceRecord sequenceRecord : samSequenceDictionary.getSequences()) {
      contigLengths.put(sequenceRecord.getSequenceName(), sequenceRecord.getSequenceLength());
    }
    SequenceDictionary sequenceDictionary = new SequenceDictionary(contigLengths, controlContigs);

    return CoordinateConverter.fromSequenceDictionary(sequenceDictionary);
  }

  public static CoordinateConverter buildConverter(SAMSequenceDictionary sequenceDictionary) {
    return buildConverter(sequenceDictionary, new ArrayList<String>());
  }

  public static CoordinateConverter buildSamOffsetConverter(SAMSequenceDictionary sequenceDictionary, int offset) {
    return buildSamOffsetConverter(sequenceDictionary.getSequences(), offset);
  }

  public static CoordinateConverter buildSamOffsetConverter(List<SAMSequenceRecord> sequences, int offset) {
    ArrayList<CoordinateConverter.ReferenceSequence> referenceSequences = new ArrayList<>();
    for (SAMSequenceRecord sequenceRecord : sequences) {
      CoordinateConverter.ReferenceSequence referenceSequence = new CoordinateConverter.ReferenceSequence(
        sequenceRecord.getSequenceName(),
        sequenceRecord.getSequenceLength()
      );

      referenceSequences.add(referenceSequence);
    }

    return new CoordinateConverter(referenceSequences, offset);
  }
}
