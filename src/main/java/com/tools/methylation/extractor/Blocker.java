package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.japi.Creator;
import akka.japi.Function;
import com.sun.management.UnixOperatingSystemMXBean;
import com.tools.actors.AbstractMaster;
import com.tools.actors.AbstractReader;
import com.tools.io.SequenceDictionary;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Path;

import static akka.actor.SupervisorStrategy.escalate;

/**
 * Responsible for blocking a large alignment file into smaller sub-components that can be processed in memory.
 */
class Blocker extends AbstractMaster {
  private final ActorRef masterRef;

  public Blocker(Path inputPath,
                 SequenceDictionary sequenceDictionary,
                 ActorRef masterRef,
                 long availableMemory,
                 int threadCount) {
    super(new Builder(inputPath, sequenceDictionary, availableMemory, threadCount), false);

    this.masterRef = masterRef;
  }

  @Override
  protected void handleCustom(Object message) {
    if (message instanceof Messages.BlocksWritten) {
      // Inform its master that the blocks have been written and shutdown its children
      masterRef.tell(message, getSelf());
      shutdown();
    } else unhandled(message);
  }

  @Override
  protected void onCompletion() {
    // Inform the master that this worker is done
    writerRef.tell(new Messages.BlockingCompleted(), getSelf());
    super.onCompletion();
  }

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return new OneForOneStrategy(
      10,
      Duration.create("1 minute"),
      new Function<Throwable, SupervisorStrategy.Directive>() {
        @Override
        public SupervisorStrategy.Directive apply(Throwable t) {
          return escalate();
        }
      }
    );
  }

  public static Props props(final Path inputPath,
                            final SequenceDictionary sequenceDictionary,
                            final ActorRef masterRef,
                            final long availableMemory,
                            final int threadCount) {
    return Props.create(new Creator<Blocker>() {
      @Override
      public Blocker create() throws Exception {
        return new Blocker(inputPath, sequenceDictionary, masterRef, availableMemory, threadCount);
      }
    });
  }

  public static class Builder extends MasterBuilder {
    private final Path inputPath;
    private final CoordinateConverter coordinateConverter;
    private final SAMFileHeader samFileHeader;
    private final int blockCount;
    private final int readerBlockSize;
    private final int referenceBlockSize;

    public Builder(Path inputPath, SequenceDictionary sequenceDictionary, long availableMemory, int threadCount) {
      super(threadCount);
      this.inputPath = inputPath;

      // Grab the SAM header so it can be added to any block sam files
      try (SAMFileReader samReader = new SAMFileReader(inputPath.toFile())) {
        samFileHeader = samReader.getFileHeader();
      }

      this.coordinateConverter = CoordinateConverter.fromSequenceDictionary(sequenceDictionary);
      this.readerBlockSize = determineBlockSize(availableMemory, workerCount);
      this.referenceBlockSize = determineReferenceBlockSize(availableMemory, workerCount);
      this.blockCount = (int) Math.ceil((double) coordinateConverter.referenceLength() / referenceBlockSize);
    }

    @Override
    protected Props readerProps(ActorRef workerRef) {
      return Reader.props(inputPath, readerBlockSize, workerRef);
    }

    @Override
    protected Props workerProps(ActorRef writerRef) {
      return BlockerWorker.props(coordinateConverter, referenceBlockSize, writerRef);
    }

    @Override
    protected Props writerProps(ActorRef masterRef) {
      return BlockerWriter.props(samFileHeader, blockCount, referenceBlockSize, coordinateConverter, masterRef);
    }

    // Determine the block size to prevent out of memory errors
    private int determineBlockSize(long availableMemory, int threadCount) {
      // The maximum number of records that any child actor will have to hold in memory
      int maxBlockLength = 100000;
      int samRecordBytes = 200;

      // Calculate the largest block length that can be used
      long blockLength = (long) Math.floor(availableMemory / (threadCount * samRecordBytes));

      return Math.min((int) blockLength, maxBlockLength);
    }

    private int determineReferenceBlockSize(long availableMemory, int threadCount) {
      // The maximum number of records that any child actor will have to hold in memory
      int maxBlockLength = 10000000;

      /** The schematic formula is
       *
       * a * M >= s * t * r + s * (t + 1) * c
       *
       * where
       *  a - ratio of total memory to use
       *  M - available memory
       *  s - block size
       *  t - thread count
       *  r - sam entry size
       *  c - count footprint
       */
      int countBytes = 8;
      int samRecordBytes = 200;

      // Calculate the largest block length that can be used
      double countCoefficient = threadCount * (samRecordBytes + countBytes) + countBytes;
      long blockLength = (long) Math.floor(availableMemory / countCoefficient);

      return Math.min((int) blockLength, maxBlockLength);
    }
  }
}
