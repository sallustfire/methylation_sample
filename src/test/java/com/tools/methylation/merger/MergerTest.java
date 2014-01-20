package com.tools.methylation.merger;

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
import java.util.*;

public class MergerTest extends ApplicationTest {
  static ActorSystem system = ActorSystem.create("TestSys");

  @Test
  public void testMerge() throws Exception {
    JavaTestKit testKit = new JavaTestKit(system);
    Props props = Merger.props(getSequenceDictionary(), testKit.getTestActor());
    TestActorRef<Merger> actorRef = TestActorRef.create(system, props);
    Merger actor = actorRef.underlyingActor();

    ArrayList<MethylationCall> results = actor.merge(getCalls(), getSequenceDictionary().contigOrderMap);
    Assert.assertEquals(193, results.size());

    MethylationCall methylationCall = new MethylationCall("gi|9626243|ref|NC_001416.1|", 1518, '-', 0, 3);
    Assert.assertEquals(methylationCall, results.get(0));
  }

  @Test
  public void testRun() throws Exception {
    final SequenceDictionary sequenceDictionary = getSequenceDictionary();

    new JavaTestKit(system) {{
      // the run() method needs to finish within 3 seconds
      new Within(duration("3 seconds")) {
        protected void run() {
          Props props = Merger.props(sequenceDictionary, getRef());
          ActorRef subject = system.actorOf(props);

          try {
            Messages.Work message = new Messages.Work(0, getCalls());
            subject.tell(message, getRef());
            expectMsgClass(duration("1 second"), Messages.MergeComplete.class);
          } catch (Exception exception) {
            exception.printStackTrace();
          }
        }
      };
    }};
  }

  private ArrayList<ArrayDeque<MethylationCall>> getCalls() throws Exception {
    List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab"),
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab"),
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab")
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
    Path inputPath = getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab");
    try(InputStream inputStream = Files.newInputStream(inputPath);
        MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
      sequenceDictionary = callReader.sequenceDictionary;
    }

    return sequenceDictionary;
  }
}
