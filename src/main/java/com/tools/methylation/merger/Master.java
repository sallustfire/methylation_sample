package com.tools.methylation.merger;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractMaster;
import com.tools.io.MethylationCallReader;
import com.tools.io.SequenceDictionary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class Master extends AbstractMaster {
  public Master(List<Path> inputPaths, Path outputPath, int threadCount) throws IOException {
    super(new Builder(inputPaths, outputPath, threadCount));
  }

  public static Props props(final List<Path> inputPaths, final Path outputPath, final int threadCount) {
    return Props.create(new Creator<Master>() {
      @Override
      public Master create() throws Exception {
        return new Master(inputPaths, outputPath, threadCount);
      }
    });
  }

  public static class Builder extends MasterBuilder {
    private final List<Path> inputPaths;
    private final SequenceDictionary sequenceDictionary;
    private final Path outputPath;

    public Builder(List<Path> inputPaths, Path outputPath, int threadCount) throws IOException {
      super(threadCount);

      // Open the call Readers
      this.inputPaths = inputPaths;
      this.outputPath = outputPath;
      this.sequenceDictionary = getConsensusDictionary(inputPaths);
    }

    @Override
    protected Props readerProps(ActorRef workerRef) {
      int blockSize = determineBlockSize(workerCount);
      return Reader.props(inputPaths, sequenceDictionary, blockSize, workerRef);
    }

    @Override
    protected Props workerProps(ActorRef writerRef) {
      return Merger.props(sequenceDictionary, writerRef);
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

    private SequenceDictionary getConsensusDictionary(List<Path> inputPaths) throws IOException {
      SequenceDictionary consensusDictionary;
      try (InputStream inputStream = Files.newInputStream(inputPaths.get(0));
           MethylationCallReader callReader = new MethylationCallReader(inputStream)) {
        consensusDictionary = callReader.sequenceDictionary;
      }

      return consensusDictionary;
    }
  }
}
