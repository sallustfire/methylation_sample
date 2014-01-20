package com.tools.methylation.caller;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.tools.ApplicationTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class CallIntegrationTest extends ApplicationTest {
  @Test
  public void testCalling() throws Exception {
    final ActorSystem system = ActorSystem.create("TestSys");

    final Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab");
    final Path outputPath = createTempFile("tab");

    new JavaTestKit(system) {{
      new Within(duration("5 seconds")) {
        protected void run() {
          try {
            Props props = Master.props(inputPath, outputPath, 0.05, 1);
            ActorRef subject = system.actorOf(props);

            // Start the run
            subject.tell(new Messages.Start(), getRef());
          } catch (IOException exception) {
            exception.printStackTrace();
          }

          new AwaitAssert(duration("5 second"), duration("100 millis")) {
            protected void check() {
              Assert.assertTrue(system.isTerminated());

              try {
                List<String> lines = Files.readAllLines(outputPath, Charset.defaultCharset());
                Assert.assertEquals(182, lines.size());

                Assert.assertEquals("#Contig\tPosition\tStrand\tMethylated\tTotal\tRatio", lines.get(10));
                Assert.assertEquals("Chr4\t3941361\t-\t1\t1\t1", lines.get(11));
                Assert.assertEquals("Chr2\t239949\t+\t0\t1\t0", lines.get(98));
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
