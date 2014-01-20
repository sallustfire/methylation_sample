package com.tools.methylation.differential;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.tools.actors.AbstractMaster;
import com.tools.methylation.utils.Region;
import com.tools.methylation.utils.RegionReader;

import java.nio.file.Path;
import java.util.List;

class Master extends AbstractMaster {
  public Master(List<Path> inputPaths, Path outputPath, List<Integer> conditions, int threadCount) {
    super(new Builder(inputPaths, outputPath, conditions, threadCount));
  }

  public Master(List<Path> inputPaths,
                Path outputPath,
                List<Integer> conditions,
                List<Region> regions,
                int threadCount) {
    super(new Builder(inputPaths, outputPath, conditions, threadCount).addRegions(regions));
  }

  public static Props props(final List<Path> inputPaths,
                            final Path outputPath,
                            final List<Integer> conditions,
                            final int threadCount) {
    return Props.create(new Creator<Master>() {
      @Override
      public Master create() throws Exception {
        return new Master(inputPaths, outputPath, conditions, threadCount);
      }
    });
  }

  public static Props props(final List<Path> inputPaths,
                            final Path outputPath,
                            final List<Integer> conditions,
                            final List<Region> regions,
                            final int threadCount) {
    return Props.create(new Creator<Master>() {
      @Override
      public Master create() throws Exception {
        return new Master(inputPaths, outputPath, conditions, regions, threadCount);
      }
    });
  }

  public static class Builder extends MasterBuilder {
    private final List<Integer> conditions;
    private final List<Path> inputPaths;
    private final Path outputPath;
    private Optional<List<Region>> regions = Optional.absent();

    public Builder(List<Path> inputPaths, Path outputPath, List<Integer> conditions, int threadCount) {
      super(threadCount);
      this.conditions = conditions;
      this.inputPaths = inputPaths;
      this.outputPath = outputPath;
    }

    public Builder addRegions(List<Region> regions) {
      this.regions = Optional.of(regions);

      return this;
    }

    @Override
    protected Props readerProps(ActorRef workerRef) {
      int blockSize = determineBlockSize(workerCount);

      Props props;
      if (regions.isPresent()) props = RegionReader.props(inputPaths, regions.get(), blockSize, workerRef);
      else props = Reader.props(inputPaths, blockSize, workerRef);

      return props;
    }

    @Override
    protected Props workerProps(ActorRef writerRef) {
      Props props;
      if (regions.isPresent()) props = RegionCaller.props(conditions, writerRef);
      else props = SiteCaller.props(conditions, writerRef);

      return props;
    }

    @Override
    protected Props writerProps(ActorRef masterRef) {
      return Writer.props(outputPath, regions.isPresent(), masterRef);
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
