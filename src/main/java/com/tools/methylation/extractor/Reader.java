package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.tools.actors.AbstractReader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;

class Reader extends AbstractReader<Messages.AlignedReadsRead> {
  private final int blockSize;
  private final PeekingIterator<SAMRecord> recordIterator;
  private final SAMFileReader fileReader;

  public Reader(Path inputPath, int blockSize, ActorRef receiverRef) throws IOException {
    super(receiverRef);

    this.blockSize = blockSize;
    this.fileReader = new SAMFileReader(inputPath.toFile());
    this.recordIterator = Iterators.peekingIterator(fileReader.iterator());
  }

  @Override
  public void postStop() throws Exception {
    super.postStop();
    fileReader.close();
  }

  @Override
  protected boolean isComplete() {
    return !recordIterator.hasNext();
  }

  @Override
  protected Messages.AlignedReadsRead read(int blockIndex) {
    // Successively read in calls for each of the regions
    ArrayDeque<AlignedFragment> fragments = readBlock(recordIterator, blockSize);

    return new Messages.AlignedReadsRead(blockIndex, fragments);
  }

  private ArrayDeque<AlignedFragment> readBlock(PeekingIterator<SAMRecord> records, int recordChunkSize) {
    // Initialize the queues
    ArrayDeque<AlignedFragment> fragments = new ArrayDeque<>();

    // Add the records
    while (fragments.size() < recordChunkSize && records.hasNext()) {
      // Get the record and ensure that it is mapped
      SAMRecord record = records.next();
      if (record.getReadUnmappedFlag()) continue;

      AlignedFragment alignedFragment;
      if (record.getReadPairedFlag() &&
          records.hasNext() &&
          !record.getMateUnmappedFlag() &&
          record.getReadName().equals(records.peek().getReadName())) {
        // The record is paired end, handle its mate too
        SAMRecord mateRecord = records.next();
        alignedFragment = new PairedEndFragment(record, mateRecord);
      } else {
        alignedFragment = new SingleEndFragment(record);
      }

      fragments.addLast(alignedFragment);
    }

    return fragments;
  }

  public static Props props(final Path inputPath,
                            final int blockSize,
                            final ActorRef receiverRef) {
    return Props.create(new Creator<Reader>() {
      @Override
      public Reader create() throws Exception {
        return new Reader(inputPath, blockSize, receiverRef);
      }
    });
  }
}
