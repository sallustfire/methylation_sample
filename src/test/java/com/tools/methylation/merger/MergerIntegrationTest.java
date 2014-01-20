package com.tools.methylation.merger;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.tools.ApplicationTest;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;
import com.tools.io.MethylationCallWriter;
import com.tools.io.SequenceDictionary;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MergerIntegrationTest extends ApplicationTest {

  @Test
  public void testColocated() throws Exception {
    final ActorSystem system = ActorSystem.create("TestSys");

    final List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab"),
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab"),
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab")
    );
    final Path outputPath = createTempFile("tab");

    // Determine the expected calls
    ArrayList<MethylationCall> inputCalls = readMethylationCalls(inputPaths.get(0));
    final ArrayList<MethylationCall> expectedCalls = new ArrayList<>();
    for (MethylationCall call : inputCalls) {
      MethylationCall adjustedCall = new MethylationCall(
        call.contig,
        call.position,
        call.strand,
        3 * call.methylatedCount,
        3 * call.totalCount
      );
      expectedCalls.add(adjustedCall);
    }

    new JavaTestKit(system) {{
      new Within(duration("5 seconds")) {
        protected void run() {
          Props props = Master.props(inputPaths, outputPath, 3);
          ActorRef subject = system.actorOf(props);

          // Start the run
          subject.tell(new Messages.Start(), getRef());

          new AwaitAssert(duration("5 second"), duration("100 millis")) {
            protected void check() {
              Assert.assertTrue(system.isTerminated());

              try {
                ArrayList<MethylationCall> results = readMethylationCalls(outputPath);
                Assert.assertEquals(expectedCalls, results);
              } catch (Exception exception) {
                exception.printStackTrace();
              }
            }
          };
        }
      };
    }};
  }

  @Test
  public void testDisparate() throws Exception {
    final ActorSystem system = ActorSystem.create("TestSys");

    // Split the input file into chunks
    Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab");
    ArrayList<MethylationCall> inputCalls = readMethylationCalls(inputPath);
    int chunkSize = (int) Math.ceil(inputCalls.size() / 3.0);

    // Get the input header
    SequenceDictionary sequenceDictionary;
    try (InputStream inputStream = Files.newInputStream(inputPath);
         MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
      sequenceDictionary = callReader.sequenceDictionary;
    }

    final ArrayList<Path> inputPaths = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Path partitionPath = createTempFile("tab");
      try (OutputStream outputStream = Files.newOutputStream(partitionPath);
           MethylationCallWriter callWriter = new MethylationCallWriter(outputStream)) {
        int stopIndex = Math.min(inputCalls.size(), (i + 1) * chunkSize);
        List<MethylationCall> partitionedCalls = inputCalls.subList(i * chunkSize, stopIndex);
        callWriter.writeHeader(sequenceDictionary);
        callWriter.write(partitionedCalls);
      }

      inputPaths.add(partitionPath);
    }

    final Path outputPath = createTempFile("tab");
    final ArrayList<MethylationCall> expectedCalls = readMethylationCalls(inputPath);

    new JavaTestKit(system) {{
      new Within(duration("5 seconds")) {
        protected void run() {
          Props props = Master.props(inputPaths, outputPath, 6);
          ActorRef subject = system.actorOf(props);

          // Start the run
          subject.tell(new Messages.Start(), getRef());

          new AwaitAssert(duration("5 second"), duration("100 millis")) {
            protected void check() {
              Assert.assertTrue(system.isTerminated());

              try {
                ArrayList<MethylationCall> results = readMethylationCalls(outputPath);
                Assert.assertEquals(expectedCalls, results);
              } catch (Exception exception) {
                exception.printStackTrace();
              }
            }
          };
        }
      };
    }};
  }

  private ArrayList<MethylationCall> readMethylationCalls(Path inputPath) throws IOException {
    ArrayList<MethylationCall> calls = new ArrayList<>();
    try (InputStream inputStream = Files.newInputStream(inputPath);
         MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
      while (callReader.hasNext()) calls.add(callReader.next());
    }

    return calls;
  }

}
