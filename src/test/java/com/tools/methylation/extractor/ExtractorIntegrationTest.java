package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.google.common.collect.ImmutableMap;
import com.tools.ApplicationTest;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallWriter;
import com.tools.io.SequenceDictionary;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMSequenceDictionary;
import net.sf.samtools.SAMSequenceRecord;
import org.junit.Assert;
import org.junit.Test;

import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ExtractorIntegrationTest extends ApplicationTest {

  @Test
  public void testMethylationSingleEndCounting() throws Exception {
    final ActorSystem system = ActorSystem.create("TestSys");

    final Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.fastq_bismark.sam");
    final Path cpgOutputPath = createTempFile("tab");
    final Path chgOutputPath = createTempFile("tab");
    final Path chhOutputPath = createTempFile("tab");
    final Path cnOutputPath = createTempFile("tab");

    final SAMSequenceDictionary samSequenceDictionary;
    try (SAMFileReader samReader = new SAMFileReader(inputPath.toFile())) {
      samSequenceDictionary = samReader.getFileHeader().getSequenceDictionary();
    }

    LinkedHashMap<String, Integer> contigLengths = new LinkedHashMap<>();
    for (SAMSequenceRecord sequenceRecord : samSequenceDictionary.getSequences()) {
      contigLengths.put(sequenceRecord.getSequenceName(), sequenceRecord.getSequenceLength());
    }
    SequenceDictionary sequenceDictionary = new SequenceDictionary(
      contigLengths,
      Arrays.asList("gi|9626243|ref|NC_001416.1|"));
    final CoordinateConverter coordinateConverter = CoordinateConverter.fromSequenceDictionary(sequenceDictionary);

    final Map<String, Path> expectations = ImmutableMap.of(
      "CpG", cpgOutputPath,
      "CHH", chhOutputPath,
      "CHG", chgOutputPath,
      "CN_CHN", cnOutputPath
    );

    // Write out the headers
    for (Map.Entry<String, Path> entry : expectations.entrySet()) {
      Path outputPath = entry.getValue();

      try (OutputStream outputStream = Files.newOutputStream(outputPath);
           MethylationCallWriter writer = new MethylationCallWriter(outputStream)) {
        writer.writeHeader(sequenceDictionary);
      }
    }

    new JavaTestKit(system) {{
      final JavaTestKit probe = new JavaTestKit(system);

      new Within(duration("5 seconds")) {
        protected void run() {
          Props props = Extractor.props(
            inputPath,
            cpgOutputPath,
            chgOutputPath,
            chhOutputPath,
            cnOutputPath,
            coordinateConverter,
            new HashMap<Character, ArrayList<MethylationCall>>(),
            probe.getRef(),
            Runtime.getRuntime().maxMemory() / 2,
            4
          );
          ActorRef subject = system.actorOf(props);

          // Start the run
          subject.tell(new Messages.Start(), getRef());

          new AwaitAssert(duration("5 second"), duration("100 millis")) {
            protected void check() {
              try {
                for (Map.Entry<String, Path> entry : expectations.entrySet()) {
                  String context = entry.getKey();
                  Path outputPath = entry.getValue();

                  String filename = "/methylation/single_end_athaliana_reads." + context + "_context.tab";
                  Path expectedPath = getFileResource(filename);
                  Assert.assertTrue(contentEquals(expectedPath, outputPath));
                  Files.delete(outputPath);
                }
              } catch (Exception exception) {
                exception.printStackTrace();
              }
            }
          };
        }
      };
    }};
  }
}
