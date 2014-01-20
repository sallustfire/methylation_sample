package com.tools.methylation.population;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.google.common.base.Optional;
import com.tools.io.MethylationCallReader;
import com.tools.io.SequenceDictionary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MethylationPopulationCaller {
  private final List<Path> inputPaths;
  private final Path outputPath;
  private final int depthCutoff;

  /**
   * Creates a MethylationCaller which identifies makes methylation calls from raw counts from one or more biological
   * replicates.
   *
   * @param inputPaths  the List<Path> to the input file with methylation counts to combine
   * @param outputPath  the Path at which to write the results
   * @param depthCutoff the Optional<Integer> number of observations under which a site is filtered
   */
  public MethylationPopulationCaller(List<Path> inputPaths, Path outputPath, Optional<Integer> depthCutoff) {
    this.inputPaths = inputPaths;
    this.outputPath = outputPath;
    this.depthCutoff = depthCutoff.or(5);
  }

  public void run(int threadCount) throws Exception {
    SequenceDictionary sequenceDictionary = getSequenceDictionary(inputPaths);
    Props props = Master.props(inputPaths, outputPath, sequenceDictionary, depthCutoff, threadCount);

    ActorSystem system = ActorSystem.create("MethylationSystem");
    ActorRef master = system.actorOf(props);

    master.tell(new Messages.Start(), master);
    system.awaitTermination();
  }

  private SequenceDictionary getSequenceDictionary(List<Path> inputPaths) throws IOException {
    SequenceDictionary sequenceDictionary;
    try (InputStream inputStream = Files.newInputStream(inputPaths.get(0))) {
      MethylationCallReader callReader = new MethylationCallReader(inputStream);
      sequenceDictionary = callReader.sequenceDictionary;
    }

    return sequenceDictionary;
  }
}
