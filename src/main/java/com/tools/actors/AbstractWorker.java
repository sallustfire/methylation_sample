package com.tools.actors;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;

public abstract class AbstractWorker<T extends AbstractMessages.Work,
                                     U extends AbstractMessages.WorkComplete> extends UntypedActor {
  private final ActorRef writerRef;

  public AbstractWorker(ActorRef writerRef) {
    this.writerRef = writerRef;
  }

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof AbstractMessages.Note) {
      System.out.println(message);
    }

    if (getWorkClass().isAssignableFrom(message.getClass())) {
      // Perform the calling
      AbstractMessages.WorkComplete workComplete = work((T) message);
      writerRef.tell(workComplete, getSelf());
    } else unhandled(message);
  }

  protected abstract Class<T> getWorkClass();
  protected abstract U work(T message);
}
