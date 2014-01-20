package com.tools.actors;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import java.util.HashMap;

public abstract class AbstractWriter<T extends AbstractMessages.WorkComplete> extends UntypedActor {
  protected final ActorRef masterRef;
  private final HashMap<Integer, T> writeQueue;

  private int nextIndex = 0;
  private boolean wroteHeader;

  public AbstractWriter(ActorRef masterRef) {
    this(masterRef, false);
  }

  public AbstractWriter(ActorRef masterRef, boolean writeHeader) {
    this.masterRef = masterRef;
    this.writeQueue = new HashMap<>();
    this.wroteHeader = !writeHeader;
  }

  @Override
  public void onReceive(Object message) throws Exception {
    if (getWorkCompleteClass().isAssignableFrom(message.getClass())) {
      T workComplete = (T) message;

      // Check to see if the header needs to be written
      if (!wroteHeader) {
        writeHeader(workComplete);
        wroteHeader = true;
      }

      // Add the completed block to the queue to be written
      writeQueue.put(workComplete.index, workComplete);

      // Attempt to drain the queue
      write();
    } else handleCustom(message);
  }

  protected void handleCustom(Object message) throws Exception { unhandled(message); }

  protected abstract Class<T> getWorkCompleteClass();
  protected abstract void write(T message);
  protected abstract void writeHeader(T message);

  private void write() {
    while (writeQueue.containsKey(nextIndex)) {
      write(writeQueue.remove(nextIndex));
      nextIndex++;

      // Message that a block has been written
      AbstractMessages.WriteComplete writeComplete = new AbstractMessages.WriteComplete();
      masterRef.tell(writeComplete, getSelf());
    }
  }
}
