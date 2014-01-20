package com.tools.methylation.caller;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Joiner;
import com.tools.actors.AbstractWriter;
import com.tools.io.MethylationCallWriter;
import com.tools.io.SequenceDictionary;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class Writer extends AbstractWriter<Messages.CallingComplete> {
  private final MethylationCallWriter writer;
  private final SequenceDictionary sequenceDictionary;

  public Writer(Path outputPath, SequenceDictionary sequenceDictionary, ActorRef masterRef) throws IOException {
    super(masterRef, true);
    this.sequenceDictionary = sequenceDictionary;
    this.writer = new MethylationCallWriter(Files.newOutputStream(outputPath));
  }

  @Override
  public void postStop() throws Exception {
    super.postStop();

    writer.close();
  }

  @Override
  protected Class<Messages.CallingComplete> getWorkCompleteClass() { return Messages.CallingComplete.class; }

  @Override
  protected void write(Messages.CallingComplete message) {
    writer.write(message.calls);
  }

  @Override
  protected void writeHeader(Messages.CallingComplete message) {
    writer.writeHeader(sequenceDictionary);
  }

  public static Props props(final Path outputPath,
                            final SequenceDictionary sequenceDictionary,
                            final ActorRef receiverRef) {
    return Props.create(new Creator<Writer>() {
      @Override
      public Writer create() throws Exception {
        return new Writer(outputPath, sequenceDictionary, receiverRef);
      }
    });
  }
}
