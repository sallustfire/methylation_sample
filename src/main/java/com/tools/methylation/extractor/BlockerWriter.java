package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.FileUtils;
import com.tools.actors.AbstractWriter;
import net.sf.samtools.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

class BlockerWriter extends AbstractWriter<Messages.ReadsBlocked> {
  private final HashMap<Integer, SAMFileWriter> writerMap;
  private final HashMap<Integer, Path> pathMap;
  private final int blockSize;
  private final CoordinateConverter coordinateConverter;

  public BlockerWriter(SAMFileHeader samHeader,
                       int blockCount,
                       int blockSize,
                       CoordinateConverter coordinateConverter,
                       ActorRef masterRef) throws IOException {
    super(masterRef, true);

    this.blockSize = blockSize;
    this.coordinateConverter = coordinateConverter;

    // Open writers for all of the blocks
    this.writerMap = new HashMap<>();
    this.pathMap = new HashMap<>();
    // TODO: Look at writing BAM file instead
    SAMFileWriterFactory samFileWriterFactory = new SAMFileWriterFactory();
    while (writerMap.size() < blockCount) {
      Path outputPath = FileUtils.createTempFile("sam");
      SAMTextWriter samWriter = (SAMTextWriter) samFileWriterFactory.makeSAMWriter(
        samHeader,
        false,
        outputPath.toFile()
      );

      pathMap.put(writerMap.size(), outputPath);
      writerMap.put(writerMap.size(), samWriter);
    }
  }

  @Override protected Class<Messages.ReadsBlocked> getWorkCompleteClass() { return Messages.ReadsBlocked.class; }

  @Override
  protected void handleCustom(Object message) throws Exception {
    if (message instanceof Messages.BlockingCompleted) {
      // Close the resources
      for (SAMFileWriter samFileWriter : writerMap.values()) samFileWriter.close();

      ArrayList<Integer> indices = new ArrayList<>(pathMap.keySet());
      Collections.sort(indices);

      // Split up the coordinates
      Iterator<CoordinateConverter> coordinateConverters = coordinateConverter.split(blockSize).iterator();

      ArrayList<BlockedReads> blockedReads = new ArrayList<>();
      for (int index : indices) {
        BlockedReads block = new BlockedReads(pathMap.get(index), coordinateConverters.next());
        blockedReads.add(block);
      }

      masterRef.tell(new Messages.BlocksWritten(blockedReads), getSelf());
    } else unhandled(message);
  }

  @Override
  protected void write(Messages.ReadsBlocked message) {
    // Merge the counts
    for (BlockerWorker.ReadBlock readBlock : message.blocks) {
      SAMFileWriter samFileWriter = writerMap.get(readBlock.index);
      for (SAMRecord samRecord : readBlock.records) samFileWriter.addAlignment(samRecord);
    }
  }

  @Override
  protected void writeHeader(Messages.ReadsBlocked message) { }

  public static Props props(final SAMFileHeader samHeader,
                            final int blockCount,
                            final int blockSize,
                            final CoordinateConverter coordinateConverter,
                            final ActorRef masterRef) {
    return Props.create(new Creator<BlockerWriter>() {
      @Override
      public BlockerWriter create() throws Exception {
        return new BlockerWriter(samHeader, blockCount, blockSize, coordinateConverter, masterRef);
      }
    });
  }
}
