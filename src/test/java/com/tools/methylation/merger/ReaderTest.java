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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReaderTest extends ApplicationTest {
  static ActorSystem system = ActorSystem.create("TestSys");

  @Test
  public void testRead() throws Exception {
    List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab"),
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab"),
      getFileResource("/methylation/single_end_athaliana_reads.CpG_context.tab")
    );

    JavaTestKit testKit = new JavaTestKit(system);
    SequenceDictionary consensusDictionary = getConsensusDictionary(inputPaths);
    Props props = Reader.props(inputPaths, consensusDictionary, 1000, testKit.getTestActor());
    TestActorRef<Reader> actorRef = TestActorRef.create(system, props);
    Reader actor = actorRef.underlyingActor();

    ArrayList<ArrayDeque<MethylationCall>> methylationCalls = actor.read(0).mergeableBlocks;
    Assert.assertEquals(methylationCalls.size(), 3);

    for (int i = 0; i < inputPaths.size(); i++) {
      Path inputPath = inputPaths.get(i);
      ArrayDeque<MethylationCall> calls = methylationCalls.get(i);

      try(InputStream inputStream = Files.newInputStream(inputPath);
          MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
        ArrayDeque<MethylationCall> expectedCalls = new ArrayDeque<>();
        while (callReader.hasNext()) expectedCalls.add(callReader.next());

        ArrayList<MethylationCall> callsList = new ArrayList<>(calls);
        ArrayList<MethylationCall> expectedCallsList = new ArrayList<>(expectedCalls);
        Assert.assertEquals(expectedCallsList, callsList);
      }
    }
  }

  private SequenceDictionary getConsensusDictionary(List<Path> inputPaths) throws IOException {
    SequenceDictionary consensusDictionary;
    try (InputStream inputStream = Files.newInputStream(inputPaths.get(0));
         MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
      consensusDictionary = callReader.sequenceDictionary;
    }

    return consensusDictionary;
  }
}
