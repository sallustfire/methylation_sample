package com.tools.methylation.caller;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.google.common.base.Optional;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;
import com.tools.io.SequenceDictionary;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;

public class MethylationRatioCaller {
  private final Path inputPath;
  private final Path outputPath;
  private final double defaultErrorRate;

  /**
   * Creates a MethylationCaller which identifies makes methylation calls from raw counts from one or more biological
   * replicates.
   *
   * @param inputPath         the Path to the input file with methylation counts
   * @param outputPath        the Path at which to write the results
   * @param defaultErrorRate  an Optional<Double> with an error rate to use if it cannot be calculated
   */
  public MethylationRatioCaller(Path inputPath, Path outputPath, Optional<Double> defaultErrorRate) {
    this.inputPath = inputPath;
    this.outputPath = outputPath;
    this.defaultErrorRate = defaultErrorRate.or(0.0);
  }

  public void run(int threadCount) throws Exception {
    Props props = Master.props(inputPath, outputPath, defaultErrorRate, threadCount);

    ActorSystem system = ActorSystem.create("MethylationSystem");
    ActorRef master = system.actorOf(props);

    master.tell(new Messages.Start(), master);
    system.awaitTermination();
  }
}
