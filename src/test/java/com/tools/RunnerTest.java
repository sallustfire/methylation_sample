package com.tools;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RunnerTest extends ApplicationTest {
  @Test
  public void testRunCaller() throws Exception {
    Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab");
    Path outputPath = createTempFile("tab");

    String[] arguments = new String[]{
      "-t", "2",
      "-i", inputPath.toString(),
      "-o", outputPath.toString()
    };
    Runner.runCaller(arguments);

    Path expectedPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.calls.tab");
    Assert.assertTrue(contentEquals(expectedPath, outputPath));
  }

  @Test
  public void testRunExtractor() throws Exception {
    Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.fastq_bismark.sam");
    Path outputPath = createTempDirectory();

    String[] arguments = new String[]{
      "-t", "4",
      "-i", inputPath.toString(),
      "-o", outputPath.toString(),
      "-c", "ChrM"
    };
    Runner.runExtractor(arguments);

    Assert.assertTrue(Files.exists(outputPath.resolve("CpG_context.tab")));
    Assert.assertTrue(Files.exists(outputPath.resolve("CHG_context.tab")));
    Assert.assertTrue(Files.exists(outputPath.resolve("CHH_context.tab")));
    Assert.assertTrue(Files.exists(outputPath.resolve("CN_CHN_context.tab")));
    Assert.assertTrue(Files.exists(outputPath.resolve("summary.tab")));
  }

//  @Test
//  public void testMerger() throws Exception {
//    Path outputPath = createTempFile("tab");
//
//    String[] arguments = new String[]{
//      "-t", "4",
//      "-i", "/Users/mcentee/Desktop/RV1Dconv_ACAGTG.CpG_context.tab.gz",
//      "-i", "/Users/mcentee/Desktop/RV1Dconv_ACAGTG2.CpG_context.tab.gz",
//      "-o", outputPath.toString(),
//    };
//    Runner.runMerger(arguments);
//
//    Path expectedPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.calls.tab");
//    Assert.assertTrue(contentEquals(expectedPath, outputPath));
//  }

//  @Test
//  public void testRegionCounter() throws Exception {
//    Path outputPath = createTempFile("tab");
//
//    String[] arguments = new String[]{
//      "-t", "6",
//      "-i", "/Volumes/Ibis/VK1Dconv.CHH_context.tab.gz",
//      "-i", "/Volumes/Ibis/VK2Dconv.CHH_context.tab.gz",
//      "-i", "/Volumes/Ibis/VK3Dconv.CHH_context.tab.gz",
//      "-r", "/Volumes/Ibis/gene_boundaries.tab",
//      "-o", outputPath.toString(),
//    };
//    Runner.runRegionCounter(arguments);
//
//    Path expectedPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.calls.tab");
//    Assert.assertTrue(contentEquals(expectedPath, outputPath));
//  }
}
