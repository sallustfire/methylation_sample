package com.tools.methylation.population;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractMaster;
import com.tools.io.SequenceDictionary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

class Master extends AbstractMaster {
  public Master(List<Path> inputPaths,
                Path outputPath,
                SequenceDictionary sequenceDictionary,
                int depthCutoff,
                int threadCount) throws IOException {
    super(new Builder(inputPaths, outputPath, sequenceDictionary, depthCutoff, threadCount));
  }

  public static Props props(final List<Path> inputPaths,
                            final Path outputPath,
                            final SequenceDictionary sequenceDictionary,
                            final int depthCutoff,
                            final int threadCount) throws IOException {
    return Props.create(new Creator<Master>() {
      @Override
      public Master create() throws Exception {
        return new Master(inputPaths, outputPath, sequenceDictionary, depthCutoff, threadCount);
      }
    });
  }

  public static class Builder extends MasterBuilder {
    private final List<Path> inputPaths;
    private final SequenceDictionary sequenceDictionary;
    private final int depthCutoff;
    private final Path outputPath;

    public Builder(List<Path> inputPaths,
                   Path outputPath,
                   SequenceDictionary sequenceDictionary,
                   int depthCutoff,
                   int threadCount) throws IOException {
      super(threadCount);

      // Open the call Reader
      this.inputPaths = inputPaths;
      this.sequenceDictionary = sequenceDictionary;
      this.depthCutoff = depthCutoff;
      this.outputPath = outputPath;
    }

    @Override
    protected Props readerProps(ActorRef workerRef) {
      int blockSize = determineBlockSize(workerCount);
      return Reader.props(inputPaths, sequenceDictionary, depthCutoff, blockSize, workerRef);
    }

    @Override
    protected Props workerProps(ActorRef writerRef) {
      return Caller.props(sequenceDictionary, writerRef);
    }

    @Override
    protected Props writerProps(ActorRef masterRef) {
      return Writer.props(outputPath, sequenceDictionary, masterRef);
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
