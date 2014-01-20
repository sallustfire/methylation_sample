package com.tools.methylation.population;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.tools.ApplicationTest;
import com.tools.io.MethylationCallReader;
import com.tools.io.SequenceDictionary;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class MethylationPopulationCallerIntegrationTest extends ApplicationTest {
  @Test
  public void testCalling() throws Exception {
    final ActorSystem system = ActorSystem.create("TestSys");

    final List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/RK1.CpG_context.calls.tab"),
      getFileResource("/methylation/RK2.CpG_context.calls.tab"),
      getFileResource("/methylation/RK3.CpG_context.calls.tab")
    );
    final Path outputPath = createTempFile("tab");

    final SequenceDictionary sequenceDictionary;
    try (InputStream inputStream = Files.newInputStream(inputPaths.get(0))) {
      MethylationCallReader callReader = new MethylationCallReader(inputStream);
      sequenceDictionary = callReader.sequenceDictionary;
    }

    new JavaTestKit(system) {{
      new Within(duration("50 seconds")) {
        protected void run() {
          try {
            Props props = Master.props(inputPaths, outputPath, sequenceDictionary, 2, 1);
            ActorRef subject = system.actorOf(props);

            // Start the run
            subject.tell(new Messages.Start(), getRef());
          } catch (IOException exception) {
            exception.printStackTrace();
          }

          new AwaitAssert(duration("50 second"), duration("100 millis")) {
            protected void check() {
              Assert.assertTrue(system.isTerminated());

              try {
                List<String> lines = Files.readAllLines(outputPath, Charset.defaultCharset());
                Assert.assertEquals(83, lines.size());

                Assert.assertEquals("#Contig\tPosition\tStrand\tRatio\tStandard Deviation", lines.get(46));
                Assert.assertEquals("7\t3000226\t-\t0.8316\t0.1684", lines.get(48));
                Assert.assertEquals("7\t3002021\t+\t1\t0", lines.get(60));
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