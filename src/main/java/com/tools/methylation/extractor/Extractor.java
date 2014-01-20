package com.tools.methylation.extractor;

import akka.actor.*;
import akka.japi.Creator;
import akka.japi.Function;
import com.tools.actors.AbstractMaster;
import com.tools.io.MethylationCall;
import scala.concurrent.duration.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static akka.actor.SupervisorStrategy.escalate;

public class Extractor extends AbstractMaster {
  private final ActorRef masterRef;

  public Extractor(Path inputPath,
                   Path cpgOutputPath,
                   Path chgOutputPath,
                   Path chhOutputPath,
                   Path cnOutputPath,
                   CoordinateConverter coordinateConverter,
                   Map<Character, ArrayList<MethylationCall>> remainders,
                   ActorRef masterRef,
                   long availableMemory,
                   int threadCount) {
    super(new Builder(
      inputPath,
      cpgOutputPath,
      chgOutputPath,
      chhOutputPath,
      cnOutputPath,
      coordinateConverter,
      remainders,
      availableMemory,
      threadCount
    ), false);

    this.masterRef = masterRef;
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

  @Override
  protected void handleCustom(Object message) {
    if (message instanceof Messages.WriteAllComplete) {
      // Message that all work has been completed
      Messages.WriteAllComplete writeAllComplete = (Messages.WriteAllComplete) message;
      Messages.BlockCountsWritten writtenMessage = new Messages.BlockCountsWritten(
        writeAllComplete.remainders,
        writeAllComplete.depthCounts
      );
      masterRef.tell(writtenMessage, getSelf());
      writerRef.tell(PoisonPill.getInstance(), getSelf());

      shutdown();
    } else unhandled(message);
  }

  @Override
  protected void onCompletion() {
    workerRef.tell(PoisonPill.getInstance(), getSelf());
    writerRef.tell(new Messages.WriteAll(), getSelf());
  }

  public static Props props(final Path inputPath,
                            final Path cpgOutputPath,
                            final Path chgOutputPath,
                            final Path chhOutputPath,
                            final Path cnOutputPath,
                            final CoordinateConverter coordinateConverter,
                            final Map<Character, ArrayList<MethylationCall>> remainders,
                            final ActorRef masterRef,
                            final long availableMemory,
                            final int threadCount) {
    return Props.create(new Creator<Extractor>() {
      @Override
      public Extractor create() throws Exception {
        return new Extractor(
          inputPath,
          cpgOutputPath,
          chgOutputPath,
          chhOutputPath,
          cnOutputPath,
          coordinateConverter,
          remainders,
          masterRef,
          availableMemory,
          threadCount
        );
      }
    });
  }

  public static class Builder extends MasterBuilder {
    private final Path inputPath;
    private final Path cpgOutputPath;
    private final Path chgOutputPath;
    private final Path chhOutputPath;
    private final Path cnOutputPath;
    private final CoordinateConverter coordinateConverter;
    private final Map<Character, ArrayList<MethylationCall>> remainders;

    private final int blockSize;

    public Builder(Path inputPath,
                   Path cpgOutputPath,
                   Path chgOutputPath,
                   Path chhOutputPath,
                   Path cnOutputPath,
                   CoordinateConverter coordinateConverter,
                   Map<Character, ArrayList<MethylationCall>> remainders,
                   long availableMemory,
                   int threadCount) {
      super(threadCount, false);
      this.inputPath = inputPath;
      this.cpgOutputPath = cpgOutputPath;
      this.chgOutputPath = chgOutputPath;
      this.chhOutputPath = chhOutputPath;
      this.cnOutputPath = cnOutputPath;
      this.coordinateConverter = coordinateConverter;
      this.remainders = remainders;

      this.blockSize = determineBlockSize(availableMemory, workerCount);
    }

    @Override
    protected Props readerProps(ActorRef workerRef) {
      return Reader.props(inputPath, blockSize, workerRef);
    }

    @Override
    protected Props workerProps(ActorRef writerRef) {
      return ExtractorCounter.props(coordinateConverter, writerRef);
    }

    @Override
    protected Props writerProps(ActorRef masterRef) {
      return ExtractorWriter.props(
        cpgOutputPath,
        chgOutputPath,
        chhOutputPath,
        cnOutputPath,
        coordinateConverter,
        remainders,
        masterRef
      );
    }

    // Determine the block size to prevent out of memory errors
    private int determineBlockSize(long availableMemory, int threadCount) {
      // The maximum number of records that any child actor will have to hold in memory
      int maxBlockLength = 100000;
      int minBlockLength = 1000;

      // Calculate the memory consumed by the counters
      long counterBytes = (threadCount + 1) * 8 * coordinateConverter.referenceLength();

      // Calculate the largest block length that can be used
      double availableThreadMemory =  (availableMemory - counterBytes) / threadCount;
      long blockLength = (long) Math.floor(availableThreadMemory / 200);

      int optimalBlockLength;
      if (blockLength > maxBlockLength) optimalBlockLength = maxBlockLength;
      else if (blockLength < minBlockLength) optimalBlockLength = minBlockLength;
      else optimalBlockLength = (int) blockLength;

      return optimalBlockLength;
    }
  }
}
