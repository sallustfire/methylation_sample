package com.tools.methylation.extractor;

import akka.actor.*;
import akka.japi.Creator;
import akka.japi.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.tools.io.MethylationCall;
import com.tools.io.SequenceDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static akka.actor.SupervisorStrategy.stop;

public class Master extends UntypedActor {
  private final Logger logger = LoggerFactory.getLogger(Master.class);

  protected final ActorRef blockerRef;
  protected ActorRef extractorRef;

  private final Path cpgOutputPath;
  private final Path chgOutputPath;
  private final Path chhOutputPath;
  private final Path cnOutputPath;
  private final Path summaryPath;

  private final long availableMemory;
  private final int threadCount;

  // Record of reads depth and methylation ratio per contig
  private final Map<String, DepthCounter> depthCounts;

  private PeekingIterator<BlockedReads> blockedReads;

  public Master(Path inputPath,
                SequenceDictionary sequenceDictionary,
                Path cpgOutputPath,
                Path chgOutputPath,
                Path chhOutputPath,
                Path cnOutputPath,
                Path summaryPath,
                long availableMemory,
                int threadCount) {
    Props blockerProps = Blocker.props(inputPath, sequenceDictionary, getSelf(), availableMemory, threadCount);
    this.blockerRef = getContext().actorOf(blockerProps, "blocker");

    this.cpgOutputPath = cpgOutputPath;
    this.chgOutputPath = chgOutputPath;
    this.chhOutputPath = chhOutputPath;
    this.cnOutputPath = cnOutputPath;
    this.summaryPath = summaryPath;

    this.availableMemory = availableMemory;
    this.threadCount = threadCount;

    this.depthCounts = new HashMap<>();
    for (String contig : sequenceDictionary.getSortedContigs()) depthCounts.put(contig, new DepthCounter());
  }

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return new AllForOneStrategy(
      10,
      Duration.create("1 minute"),
      new Function<Throwable, SupervisorStrategy.Directive>() {
        @Override
        public SupervisorStrategy.Directive apply(Throwable t) {
          shutdown();
          return stop();
        }
      }
    );
  }

  public static Props props(final Path inputPath,
                            final SequenceDictionary sequenceDictionary,
                            final Path cpgOutputPath,
                            final Path chgOutputPath,
                            final Path chhOutputPath,
                            final Path cnOutputPath,
                            final Path summaryPath,
                            final long availableMemory,
                            final int threadCount) {
    return Props.create(new Creator<Master>() {
      @Override
      public Master create() throws Exception {
        return new Master(
          inputPath,
          sequenceDictionary,
          cpgOutputPath,
          chgOutputPath,
          chhOutputPath,
          cnOutputPath,
          summaryPath,
          availableMemory,
          threadCount
        );
      }
    });
  }

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof Messages.Start) start((Messages.Start) message);
    else if (message instanceof Messages.BlocksWritten) blocksWritten((Messages.BlocksWritten) message);
    else if (message instanceof Messages.BlockCountsWritten) countsWritten((Messages.BlockCountsWritten) message);
    else unhandled(message);
  }

  protected void shutdown() {
    // Write out the details
    writeStatistics(depthCounts, summaryPath);

    getContext().system().shutdown();
  }

  private void extractBlock(BlockedReads blockedReads, Map<Character, ArrayList<MethylationCall>> remainders) {
    // Discard empty blocks
    CoordinateConverter coordinateConverter = blockedReads.coordinateConverter;
    logger.info("Extracting to {}:{}", coordinateConverter.stopContig, coordinateConverter.stopPosition);

    Props extractorProps = Extractor.props(
      blockedReads.path,
      cpgOutputPath,
      chgOutputPath,
      chhOutputPath,
      cnOutputPath,
      coordinateConverter,
      remainders,
      getSelf(),
      availableMemory,
      threadCount
    );
    this.extractorRef = getContext().actorOf(extractorProps);

    extractorRef.tell(new Messages.Start(), getSelf());
  }

  private void start(Messages.Start message) { blockerRef.tell(message, getSelf()); }

  private void blocksWritten(Messages.BlocksWritten message) {
    this.blockedReads = Iterators.peekingIterator(message.blockedReads.iterator());
    while (blockedReads.hasNext() && blockedReads.peek().isEmpty()) blockedReads.next();

    if (blockedReads.hasNext()) extractBlock(blockedReads.next(), new HashMap<Character, ArrayList<MethylationCall>>());
    else shutdown();
  }

  private void countsWritten(Messages.BlockCountsWritten message) {
    // Log the statistics
    recordStatistics(message.depthCounts);

    // Drop empty blocks
    while (blockedReads.hasNext() && blockedReads.peek().isEmpty()) blockedReads.next();

    if (blockedReads.hasNext()) extractBlock(blockedReads.next(), message.remainders);
    else shutdown();
  }

  private void recordStatistics(Map<String, DepthCounter> depthCounts) {
    for (Map.Entry<String, DepthCounter> entry : depthCounts.entrySet()) {
      this.depthCounts.get(entry.getKey()).merge(entry.getValue());
    }
  }

  private void writeStatistics(Map<String, DepthCounter> depthCounts, Path summaryPath) {
    try (OutputStream outputStream = Files.newOutputStream(summaryPath);
         PrintWriter writer = new PrintWriter(outputStream)) {
      writer.println("Contig\tDepth\tMethylation Ratio");

      DepthCounter cumulativeCounter = new DepthCounter();
      for (Map.Entry<String, DepthCounter> entry : depthCounts.entrySet()) {
        DepthCounter depthCounter = entry.getValue();
        cumulativeCounter.merge(depthCounter);
        writer.println(entry.getKey() + "\t" + depthCounter.meanDepth() + "\t" + depthCounter.methylationRate());
      }
      writer.println("All\t" + cumulativeCounter.meanDepth() + "\t" + cumulativeCounter.methylationRate());
    } catch (IOException exception) {
      logger.error("Unable to write summary file {}", summaryPath);
    }
  }
}
