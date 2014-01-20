package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractWorker;
import net.sf.samtools.SAMRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class BlockerWorker extends AbstractWorker<Messages.AlignedReadsRead, Messages.ReadsBlocked> {
  private final CoordinateConverter coordinateConverter;
  private final int binSize;

  public BlockerWorker(CoordinateConverter coordinateConverter, int binSize, ActorRef writerRef) {
    super(writerRef);
    this.coordinateConverter = coordinateConverter;
    this.binSize = binSize;
  }

  @Override protected Class<Messages.AlignedReadsRead> getWorkClass() { return Messages.AlignedReadsRead.class; }

  @Override
  protected Messages.ReadsBlocked work(Messages.AlignedReadsRead message) {
    ArrayList<ReadBlock> readBlocks = block(message.fragments);

    return new Messages.ReadsBlocked(message.index, readBlocks);
  }

  private ArrayList<ReadBlock> block(ArrayDeque<AlignedFragment> fragments) {
    HashMap<Integer, ArrayDeque<SAMRecord>> blockMap = new HashMap<>();

    for (AlignedFragment fragment : fragments) {
      long startPosition = coordinateConverter.convert(fragment.contig(), fragment.start());

      // Identify the bin that the fragment belongs to
      int binIndex = (int) Math.floor((double) startPosition / binSize);

      if (!blockMap.containsKey(binIndex)) blockMap.put(binIndex, new ArrayDeque<SAMRecord>());
      ArrayDeque<SAMRecord> blockRecords = blockMap.get(binIndex);

      if (fragment instanceof SingleEndFragment) {
        blockRecords.addLast(((SingleEndFragment) fragment).read);
      } else {
        PairedEndFragment pairedEndFragment = (PairedEndFragment) fragment;
        blockRecords.addLast(pairedEndFragment.read1);
        blockRecords.addLast(pairedEndFragment.read2);
      }
    }

    ArrayList<ReadBlock> readBlocks = new ArrayList<>();
    for (Map.Entry<Integer, ArrayDeque<SAMRecord>> entry : blockMap.entrySet()) {
      ReadBlock readBlock = new ReadBlock(entry.getKey(), entry.getValue());
      readBlocks.add(readBlock);
    }

    return readBlocks;
  }

  public static Props props(final CoordinateConverter coordinateConverter,
                            final int binSize,
                            final ActorRef receiverRef) {
    return Props.create(new Creator<BlockerWorker>() {
      @Override
      public BlockerWorker create() throws Exception {
        return new BlockerWorker(coordinateConverter, binSize, receiverRef);
      }
    });
  }

  class ReadBlock {
    public final int index;
    public final ArrayDeque<SAMRecord> records;

    public ReadBlock(int index, ArrayDeque<SAMRecord> records) {
      this.index = index;
      this.records = records;
    }
  }
}
