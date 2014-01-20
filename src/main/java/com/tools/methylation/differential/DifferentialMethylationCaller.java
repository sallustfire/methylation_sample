package com.tools.methylation.differential;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.tools.methylation.utils.Region;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Identifies locations with differential methylation.
 */
public class DifferentialMethylationCaller {
  private final List<Path> inputPaths;
  private final ArrayList<Integer> conditions;
  private final Path outputPath;
  private final Optional<Path> regionsPath;

  /**
   * Creates a DifferentialMethylationCaller which identifies statistically significant differentially methylated sites
   * across the samples.
   *
   * @param inputPaths   a List<Path> to the input files with methylation counts
   * @param conditions   a List<Integer> with the sample conditions
   * @param outputPath   the Path at which to write the results
   * @param regionsPath  an Optional<Path> with explicit regions to analyze
   */
  public DifferentialMethylationCaller(List<Path> inputPaths,
                                       List<Integer> conditions,
                                       Path outputPath,
                                       Optional<Path> regionsPath) {
    if (conditions.size() != inputPaths.size()) {
      throw new IllegalArgumentException("the number of conditions must match the number of inputs");
    }

    this.inputPaths = inputPaths;
    this.conditions = new ArrayList<>(conditions);
    this.outputPath = outputPath;
    this.regionsPath = regionsPath;
  }

  public void run(int threadCount) throws Exception {
    // Create the thread system
    ActorSystem system = ActorSystem.create("DifferentialMethylationSystem");

    Props props;
    if (regionsPath.isPresent()) {
      ArrayList<Region> regions = parseRegions(regionsPath.get());
      props = Master.props(inputPaths, outputPath, conditions, regions, threadCount);
    } else props = Master.props(inputPaths, outputPath, conditions, threadCount);
    ActorRef master = system.actorOf(props);

    master.tell(new Messages.Start(), master);
    system.awaitTermination();
  }

  private ArrayList<Region> parseRegions(Path regionsPath) throws IOException {
    Splitter splitter = Splitter.on("\t");

    ArrayList<Region> regions = new ArrayList<>();
    for (String line : Files.readAllLines(regionsPath, Charset.defaultCharset())) {
      Iterable<String> fields = splitter.split(line);
      Iterator<String> fieldIterator = fields.iterator();

      String id = fieldIterator.next();
      String contig = fieldIterator.next();
      int start = Integer.parseInt(fieldIterator.next());
      int stop = Integer.parseInt(fieldIterator.next());
      regions.add(new Region(id, contig, start, stop));
    }

    return regions;
  }
}
