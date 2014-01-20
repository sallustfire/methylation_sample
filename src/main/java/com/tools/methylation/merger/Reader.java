package com.tools.methylation.merger;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.tools.actors.AbstractReader;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallReader;
import com.tools.io.SequenceDictionary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads common chunks from a collection of input files and signals that these chunks can be merged together.
 */
class Reader extends AbstractReader<Messages.Work> {
  private final ArrayList<MethylationCallReader> callReaders;
  private final SequenceDictionary sequenceDictionary;
  private final int maxWorkSize;

  public Reader(List<Path> inputPaths,
                SequenceDictionary sequenceDictionary,
                int maxWorkSize,
                ActorRef workerRef) throws IOException {
    super(workerRef);

    // Open all of the files and read in the headers
    this.callReaders = new ArrayList<>();
    for (Path inputPath : inputPaths) callReaders.add(new MethylationCallReader(Files.newInputStream(inputPath)));

    this.maxWorkSize = maxWorkSize;
    this.sequenceDictionary = sequenceDictionary;
  }

  @Override protected boolean isComplete() { return !nextReaderIndex().isPresent(); }

  @Override
  public void postStop() throws Exception {
    super.postStop();

    // Close any open file handles
    for (MethylationCallReader callReader : callReaders) callReader.close();
  }

  @Override
  protected Messages.Work read(int blockIndex) {
    ArrayList<ArrayDeque<MethylationCall>> extractedCalls = new ArrayList<>(callReaders.size());

    // Read in from all the inputs
    int index = nextReaderIndex().get();
    ArrayDeque<MethylationCall> firstChunk = readCalls(callReaders.get(index));
    MethylationCall boundaryCall = firstChunk.getLast();

    // Add the first block
    extractedCalls.add(firstChunk);
    for (MethylationCallReader callReader : callReaders.subList(index, callReaders.size())) {
      ArrayDeque<MethylationCall> calls = readCalls(callReader, boundaryCall.contig, boundaryCall.position);
      if (!calls.isEmpty()) extractedCalls.add(calls);
    }

    return new Messages.Work(blockIndex, extractedCalls);
  }

  /**
   * Returns the index of the first reader with unread entries.
   */
  private Optional<Integer> nextReaderIndex() {
    Optional<Integer> index = Optional.absent();
    for (int i = 0; i < callReaders.size(); i++) {
      if (callReaders.get(i).hasNext()) {
        index = Optional.of(i);
        break;
      }
    }

    return index;
  }

  private ArrayDeque<MethylationCall> readCalls(MethylationCallReader callReader) {
    ArrayDeque<MethylationCall> calls = new ArrayDeque<>();
    while (callReader.hasNext() && calls.size() < maxWorkSize) calls.addLast(callReader.next());

    return calls;
  }

  private ArrayDeque<MethylationCall> readCalls(MethylationCallReader callReader, String stopContig, int stopPosition) {
    ArrayDeque<MethylationCall> calls = new ArrayDeque<>();

    int stopIndex = sequenceDictionary.getContigIndex(stopContig);
    while (callReader.hasNext() && callPrecedes(callReader.peek(), stopIndex, stopPosition)) {
      calls.addLast(callReader.next());
    }

    return calls;
  }

  private boolean callPrecedes(MethylationCall methylationCall, int contigIndex, int position) {
    int callContigIndex = sequenceDictionary.getContigIndex(methylationCall.contig);
    return (callContigIndex < contigIndex) || (callContigIndex == contigIndex && methylationCall.position <= position);
  }

  public static Props props(final List<Path> inputPaths,
                            final SequenceDictionary sequenceDictionary,
                            final int maxWorkSize,
                            final ActorRef receiverRef) {
    return Props.create(new Creator<Reader>() {
      @Override
      public Reader create() throws Exception {
        return new Reader(inputPaths, sequenceDictionary, maxWorkSize, receiverRef);
      }
    });
  }
}
