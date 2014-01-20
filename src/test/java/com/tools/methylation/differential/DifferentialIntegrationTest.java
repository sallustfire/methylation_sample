package com.tools.methylation.differential;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.tools.ApplicationTest;
import com.tools.methylation.utils.Region;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class DifferentialIntegrationTest extends ApplicationTest {

  @Test
  public void testSiteCalling() throws Exception {
    final ActorSystem system = ActorSystem.create("TestSys");

    final List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/VK1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK3Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV3Dconv.CpG_context.tab")
    );
    final List<Integer> conditions = Arrays.asList(1, 1, 1, 0, 0, 0);
    final Path outputPath = createTempFile("tab");

    new JavaTestKit(system) {{
      new Within(duration("5 seconds")) {
        protected void run() {
          Props props = Master.props(inputPaths, outputPath, conditions, 3);
          ActorRef subject = system.actorOf(props);

          // Start the run
          subject.tell(new Messages.Start(), getRef());

          new AwaitAssert(duration("5 second"), duration("100 millis")) {
            protected void check() {
              Assert.assertTrue(system.isTerminated());

              try {
                List<String> lines = Files.readAllLines(outputPath, Charset.defaultCharset());
                List<String> expected = Arrays.asList(
                  "Contig\tPosition\tStrand\tSample 1 Mean\tSample 2 Mean\tT Statistic\tP-value",
                  "19\t5795273\t-\t0.417\t0.808\t-6.76\t0.01773223469219714"
                );
                Assert.assertEquals(expected, lines);
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
  public void testRegionCalling() throws Exception {
    final ActorSystem system = ActorSystem.create("TestSys");

    final List<Region> regions = Arrays.asList(
      new Region("Malat1", "19", 5795690, 5802671),
      new Region("1", "2", 3051244, 3054244),
      new Region("2", "2", 5795690, 5802671)
    );

    final List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/VK1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK3Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV3Dconv.CpG_context.tab")
    );
    final List<Integer> conditions = Arrays.asList(1, 1, 1, 0, 0, 0);
    final Path outputPath = createTempFile("tab");

    new JavaTestKit(system) {{
      new Within(duration("5 seconds")) {
        protected void run() {
          Props props = Master.props(inputPaths, outputPath, conditions, regions, 3);
          ActorRef subject = system.actorOf(props);

          // Start the run
          subject.tell(new Messages.Start(), getRef());

          new AwaitAssert(duration("5 second"), duration("100 millis")) {
            protected void check() {
              Assert.assertTrue(system.isTerminated());

              try {
                List<String> lines = Files.readAllLines(outputPath, Charset.defaultCharset());
                List<String> expected = Arrays.asList(
                  "Id\tContig\tStart\tStop\tSample 1 Mean\tSample 2 Mean\tT Statistic\tP-value",
                  "1\t2\t3051244\t3054244\t0.775\t0.844\t-1.48\t0.1419107639184901",
                  "2\t2\t5795690\t5802671\tNA\tNA\tNA\tNA",
                  "Malat1\t19\t5795690\t5802671\t0.267\t0.190\t2.91\t0.0037884821690257198"
                );
                Assert.assertEquals(expected, lines);
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

