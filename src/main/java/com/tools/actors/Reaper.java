package com.tools.actors;

import akka.actor.*;
import akka.japi.Creator;

import java.util.ArrayList;

/**
 * Watches actors and terminates itself when all of the watched actors have been gracefully terminated providing a hook
 * for any actor that watches it.
 */
public class Reaper extends UntypedActor {
  private final ArrayList<ActorRef> watchedActors = new ArrayList<>();

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof Terminated) terminate((Terminated) message);
    else if (message instanceof AbstractMessages.WatchMe) watchActor((AbstractMessages.WatchMe) message);
    else unhandled(message);
  }

  private void terminate(Terminated message) {
    watchedActors.remove(message.actor());
    if (watchedActors.isEmpty()) getSelf().tell(PoisonPill.getInstance(), getSelf());
  }

  private void watchActor(AbstractMessages.WatchMe message) {
    getContext().watch(message.actorRef);
    watchedActors.add(message.actorRef);
  }

  public static Props props() {
    return Props.create(new Creator<Reaper>() {
      @Override public Reaper create() throws Exception { return new Reaper(); }
    });
  }
}
