package com.tools.actors;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.google.common.base.Optional;

/**
 * Schematic Actor for reading from an input and signaling to sibling worker actors that input has been read.
 */
public abstract class AbstractReader<T extends AbstractMessages.Work> extends UntypedActor {
  private final ActorRef workerRef;
  private int nextBlockIndex = 0;

  public AbstractReader(ActorRef workerRef) {
    this.workerRef = workerRef;
  }

  /**
   * Returns true if the reader is done reading.
   *
   * @return boolean indicating if more can be read
   */
  protected abstract boolean isComplete();
  protected abstract T read(int blockIndex);

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof AbstractMessages.Read) {
      // Signal that no more blocks are available
      if (isComplete()) getSender().tell(new AbstractMessages.AllRead(), getSelf());
      else {
        // Read in from all the inputs
        T work = read(nextBlockIndex);

        // Signal that a block was read
        AbstractMessages.ReadComplete readCompleteMessage = new AbstractMessages.ReadComplete();
        getSender().tell(readCompleteMessage, getSelf());

        // Signal that the block should be processed
        workerRef.tell(work, getSelf());
        nextBlockIndex++;

      }
    } else unhandled(message);
  }
}
