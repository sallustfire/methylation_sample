package com.tools.methylation.differential;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import com.tools.ApplicationTest;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;
import com.tools.io.SequenceDictionary;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SiteCallerTest extends ApplicationTest {
  static ActorSystem system = ActorSystem.create("TestSys");

  ArrayList<Integer> conditions = new ArrayList<>(Arrays.asList(1, 1, 1, 0, 0, 0));

  @Test
  public void testCall() throws Exception {
    JavaTestKit testKit = new JavaTestKit(system);
    Props props = SiteCaller.props(conditions, testKit.getTestActor());
    TestActorRef<SiteCaller> actorRef = TestActorRef.create(system, props);
    SiteCaller actor = actorRef.underlyingActor();

    ArrayList<DifferentialCall> results = actor.call(
      getCalls(),
      conditions,
      getSequenceDictionary().contigOrderMap
    );
    Assert.assertEquals(1, results.size());

    DifferentialSiteCall expected =
      new DifferentialSiteCall("19", 5795273, '-', 0.417, 0.808, -6.757, 0.0177);
    Assert.assertEquals(expected, results.get(0));
  }

  @Test
  public void testRun() throws Exception {
    new JavaTestKit(system) {{
      // the run() method needs to finish within 3 seconds
      new Within(duration("3 seconds")) {
        protected void run() {
          Props props = SiteCaller.props(conditions, getRef());
          ActorRef subject = system.actorOf(props);

          try {
            Messages.Call message = new Messages.Call(0, getCalls(), getSequenceDictionary());
            subject.tell(message, getRef());
            expectMsgClass(duration("1 second"), Messages.CallingComplete.class);
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        }
      };
    }};
  }

  private ArrayList<ArrayDeque<MethylationCall>> getCalls() throws Exception {
    List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/VK1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK3Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV3Dconv.CpG_context.tab")
    );

    ArrayList<ArrayDeque<MethylationCall>> methylationCalls = new ArrayList<>();
    for (Path inputPath : inputPaths) {
      try(InputStream inputStream = Files.newInputStream(inputPath);
          MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
        ArrayDeque<MethylationCall> expectedCalls = new ArrayDeque<>();
        while (callReader.hasNext()) expectedCalls.add(callReader.next());
        methylationCalls.add(expectedCalls);
      }
    }

    return methylationCalls;
  }

  private SequenceDictionary getSequenceDictionary() throws Exception {
    SequenceDictionary sequenceDictionary;
    Path inputPath = getFileResource("/methylation/VK1Dconv.CpG_context.tab");
    try(InputStream inputStream = Files.newInputStream(inputPath);
        MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
      sequenceDictionary = callReader.sequenceDictionary;
    }

    return sequenceDictionary;
  }
}
