package com.tools.methylation.merger;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.tools.io.MethylationCallReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges two or more methylation call files over the same coordinate space together.
 */
public class MethylationCallMerger {
  private final List<Path> inputPaths;
  private final Path outputPath;

  public MethylationCallMerger(List<Path> inputPaths, Path outputPath) {
    this.inputPaths = inputPaths;
    this.outputPath = outputPath;
  }

  public void run(int threadCount) throws Exception {
    // Create the thread system
    Props props = Master.props(inputPaths, outputPath, threadCount);
    ActorSystem system = ActorSystem.create("MergerSystem");
    ActorRef master = system.actorOf(props);

    master.tell(new Messages.Start(), master);
    system.awaitTermination();
  }
}
