package com.tools.actors;

import akka.actor.ActorRef;

public class AbstractMessages {
  public static class AllRead { }
  public static class Read { }
  public static class ReadComplete { }
  public static class Start { }
  public static class WriteComplete { }

  public static class Note {
    public final String message;
    public Note(String message) { this.message = message; }
  }

  public static class WatchMe {
    public final ActorRef actorRef;

    public WatchMe(ActorRef actorRef) { this.actorRef = actorRef; }
  }

  public static abstract class Work {
    public final int index;

    public Work(int index) {
      this.index = index;
    }
  }

  public static abstract class WorkComplete {
    public final int index;

    public WorkComplete(int index) {
      this.index = index;
    }
  }
}
