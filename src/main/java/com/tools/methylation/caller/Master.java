package com.tools.methylation.caller;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractMaster;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/**
 * Calculate the most likely methylation ratios for each site given the corresponding count data.  It corrects the ratio
 * based on the estimated or provided error rate.
 */
class Master extends AbstractMaster {
  public Master(Path inputPath, Path outputPath, double errorRate, int threadCount) throws IOException {
    super(new Builder(inputPath, outputPath, errorRate, threadCount));
  }

  public static Props props(final Path inputPath,
                            final Path outputPath,
                            final double errorRate,
                            final int threadCount) throws IOException {
    return Props.create(new Creator<Master>() {
      @Override
      public Master create() throws Exception {
        return new Master(inputPath, outputPath, errorRate, threadCount);
      }
    });
  }

  public static class Builder extends MasterBuilder {
    private final MethylationCallReader callReader;
    private final double errorRate;
    private final Path outputPath;

    public Builder(Path inputPath,
                   Path outputPath,
                   double defaultErrorRate, int threadCount) throws IOException {
      super(threadCount);

      // Open the call Reader
      this.callReader = new MethylationCallReader(Files.newInputStream(inputPath));
      this.errorRate = calculateErrorRate(callReader, defaultErrorRate);
      this.outputPath = outputPath;
    }

    @Override
    protected Props readerProps(ActorRef workerRef) {
      int blockSize = determineBlockSize(workerCount);
      return Reader.props(callReader, blockSize, workerRef);
    }

    @Override
    protected Props workerProps(ActorRef writerRef) {
      return Caller.props(errorRate, writerRef);
    }

    @Override
    protected Props writerProps(ActorRef masterRef) {
      return Writer.props(outputPath, callReader.sequenceDictionary, masterRef);
    }

    private static double calculateErrorRate(MethylationCallReader callReader, double defaultRate) {
      // Identify the control contigs
      HashSet<String> contigs = new HashSet<>(callReader.sequenceDictionary.controlContigs);

      int methylatedCount = 0;
      int totalCount = 0;
      while (callReader.hasNext() && contigs.contains(callReader.peek().contig)) {
        MethylationCall call = callReader.next();
        methylatedCount += call.methylatedCount;
        totalCount += call.totalCount;
      }

      // Calculate the observed rate
      double rate;
      if (totalCount > 0) rate = (double) methylatedCount / totalCount;
      else rate = defaultRate;

      return rate;
    }

    // Determine the block size to prevent out of memory errors
    private static int determineBlockSize(int threadCount) {
      // The maximum number of records that any child actor will have to hold in memory
      int maxBlockSize = 100000;

      double availableThreadMemory = 0.8 * Runtime.getRuntime().maxMemory() / threadCount;
      long maxBlockLength = (long) Math.floor(availableThreadMemory / 200);
      return Math.min((int) maxBlockLength, maxBlockSize);
    }
  }
}
