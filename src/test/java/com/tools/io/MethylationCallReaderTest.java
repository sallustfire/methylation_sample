package com.tools.io;

import com.tools.ApplicationTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class MethylationCallReaderTest extends ApplicationTest {
  @Test
  public void testSequenceDictionary() throws Exception {
    Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab");

    SequenceDictionary sequenceDictionary;
    try (InputStream inputStream = Files.newInputStream(inputPath);
         MethylationCallReader methylationCallReader = new MethylationCallReader(inputStream)) {
      sequenceDictionary = methylationCallReader.sequenceDictionary;
    }

    List<String> expectedControlContig = Arrays.asList("gi|9626243|ref|NC_001416.1|");
    Assert.assertEquals(expectedControlContig, sequenceDictionary.controlContigs);

    LinkedHashMap<String, Integer> expectedLengthMap = new LinkedHashMap<>();
    expectedLengthMap.put("gi|9626243|ref|NC_001416.1|", 48502);
    expectedLengthMap.put("Chr4", 18585056);
    expectedLengthMap.put("Chr1", 30427671);
    expectedLengthMap.put("Chr3", 23459830);
    expectedLengthMap.put("ChrC", 154478);
    expectedLengthMap.put("Chr2", 19698289);
    expectedLengthMap.put("ChrM", 366924);
    expectedLengthMap.put("Chr5", 26975502);
    Assert.assertEquals(expectedLengthMap, sequenceDictionary.contigLengthMap);
  }

  @Test
  public void testHasNext() throws Exception {
    Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab");

    try (InputStream inputStream = Files.newInputStream(inputPath);
         MethylationCallReader methylationCallReader = new MethylationCallReader(inputStream)) {
      Assert.assertTrue(methylationCallReader.hasNext());
      for (int i = 0; i < 193; i++) methylationCallReader.next();
      Assert.assertFalse(methylationCallReader.hasNext());
    }
  }

  @Test
  public void testNext() throws Exception {
    Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab");

    try (InputStream inputStream = Files.newInputStream(inputPath);
         MethylationCallReader methylationCallReader = new MethylationCallReader(inputStream)) {
      MethylationCall result = methylationCallReader.next();
      MethylationCall expected = new MethylationCall(
        "gi|9626243|ref|NC_001416.1|",
        1518,
        '-',
        0,
        1
      );
      Assert.assertEquals(result, expected);
    }
  }
}
