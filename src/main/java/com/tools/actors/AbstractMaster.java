package com.tools.actors;

import akka.actor.*;
import akka.japi.Function;
import akka.pattern.Patterns;
import akka.routing.Broadcast;
import akka.routing.RoundRobinRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import static akka.actor.SupervisorStrategy.escalate;
import static akka.actor.SupervisorStrategy.stop;

/**
 * Provides the actor structure for a pool with a single reader, a single writer, and many workers.
 */
public abstract class AbstractMaster extends UntypedActor {
  private final Logger logger = LoggerFactory.getLogger(AbstractMaster.class);

  protected final ActorRef readerRef;
  protected final ActorRef workerRef;
  protected final ActorRef writerRef;

  // Puts the children on death watch and terminates when all of the children have been terminated
  private final ActorRef reaperRef;

  // Indicates if all of blocks have been read and the reader has no more work to do
  private boolean allRead = false;

  // The int number of reads that have been successfully completed
  private int readCount = 0;

  // Indicates if the entire system should be shutdown when this actor shuts down
  private final boolean systemShutdown;

  // The int number of workers
  private final int workerCount;

  // Indicates whether internal status should be logged
  private boolean verbose;

  // The int number of block that have been successfully written
  private int writtenCount = 0;

  /**
   * Creates the AbstractMaster actor and all of it's children.
   *
   */
  public AbstractMaster(MasterBuilder builder, boolean systemShutdown) {
    this.writerRef = getContext().actorOf(builder.writerProps(getSelf()), "writer");

    this.workerCount = builder.workerCount;
    Props workerProps = builder.workerProps(writerRef).withRouter(new RoundRobinRouter(workerCount));
    this.workerRef = getContext().actorOf(workerProps, "worker");

    this.readerRef = getContext().actorOf(builder.readerProps(workerRef), "reader");

    // Set up the reaper for graceful shutdown
    this.reaperRef = getContext().actorOf(Reaper.props());
    reaperRef.tell(new AbstractMessages.WatchMe(readerRef), getSelf());
    reaperRef.tell(new AbstractMessages.WatchMe(workerRef), getSelf());
    reaperRef.tell(new AbstractMessages.WatchMe(writerRef), getSelf());
    getContext().watch(reaperRef);

    this.verbose = builder.verbose;
    this.systemShutdown = systemShutdown;
  }
  public AbstractMaster(MasterBuilder builder) { this(builder, true); }

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof AbstractMessages.Start) processStart((AbstractMessages.Start) message);
    else if (message instanceof AbstractMessages.ReadComplete) {
      processReadComplete((AbstractMessages.ReadComplete) message);
    } else if (message instanceof AbstractMessages.WriteComplete) {
      processWriteComplete((AbstractMessages.WriteComplete) message);
    } else if (message instanceof AbstractMessages.AllRead) processAllRead((AbstractMessages.AllRead) message);
    else if (message instanceof Terminated && ((Terminated) message).actor().equals(reaperRef)) shutdown();
    else handleCustom(message);
  }

  @Override
  public SupervisorStrategy supervisorStrategy() {
    // Ensure that exceptions shutdown the entire system by default
    return new AllForOneStrategy(
      10,
      Duration.create("1 minute"),
      new Function<Throwable, SupervisorStrategy.Directive>() {
        @Override
        public SupervisorStrategy.Directive apply(Throwable t) {
          shutdown();
          return stop();
        }
      }
    );
  }

  protected void handleCustom(Object message) { unhandled(message); }
  protected void onCompletion() {
    // The process is complete so shutdown
    workerRef.tell(new Broadcast(PoisonPill.getInstance()), getSelf());
    writerRef.tell(PoisonPill.getInstance(), getSelf());
  }

  /**
   * Shuts down the actor system attempting to wait for the child actors to gracefully terminate.
   */
  protected void shutdown() {
    if (systemShutdown) getContext().system().shutdown();
    else getSelf().tell(PoisonPill.getInstance(), getSelf());
  }

  /**
   * Returns true if all of the blocks have been read and have been written.
   */
  private boolean isComplete() { return allRead && readCount == writtenCount; }

  private void processAllRead(AbstractMessages.AllRead allRead) {
    if (verbose) logger.info("Finished Reading Blocks");

    // Tell the reader to kill itself
    if (!this.allRead) {
      readerRef.tell(PoisonPill.getInstance(), getSelf());
      this.allRead = true;
    }

    // Determine if the process is done or another block needs to be kicked off
    if (isComplete()) {
      if (verbose) logger.info("Finished Writing Blocks");
      onCompletion();
    }
  }

  private void processStart(AbstractMessages.Start start) { sendRead(); }

  private void processReadComplete(AbstractMessages.ReadComplete readComplete) {
    // Note that an outstanding block has been read
    readCount++;
    if (verbose) logger.info("Read block {}", readCount);

    // Read another block if there is a free worker
    if (readCount - writtenCount <= workerCount) sendRead();
  }

  private void processWriteComplete(AbstractMessages.WriteComplete writeComplete) {
    writtenCount += 1;
    if (verbose) logger.info("Wrote block {}", writtenCount);

    // Determine if the process is done or another block needs to be kicked off
    if (isComplete()) {
      if (verbose) logger.info("Finished Writing Blocks");
      onCompletion();
    }
    else sendRead();
  }

  /**
   * Tells the reader to read the next block.
   */
  private void sendRead() {
    if (!allRead) readerRef.tell(new AbstractMessages.Read(), getSelf());
  }

  protected static abstract class MasterBuilder {
    public final boolean verbose;
    public final int workerCount;

    public MasterBuilder(int threadCount) { this(threadCount, true); }
    public MasterBuilder(int threadCount, boolean verbose) {
      this.verbose = verbose;
      this.workerCount = Math.max(threadCount - 2, 1);
    }

    protected abstract Props readerProps(ActorRef workerRef);
    protected abstract Props workerProps(ActorRef writerRef);
    protected abstract Props writerProps(ActorRef masterRef);
  }
}
