package com.tools.methylation.merger;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.tools.actors.AbstractWriter;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallWriter;
import com.tools.io.SequenceDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class Writer extends AbstractWriter<Messages.MergeComplete> {
  private final MethylationCallWriter writer;
  private final SequenceDictionary sequenceDictionary;

  public Writer(Path outputPath, SequenceDictionary sequenceDictionary, ActorRef masterRef) throws IOException {
    super(masterRef, true);

    this.writer = new MethylationCallWriter(Files.newOutputStream(outputPath));
    this.sequenceDictionary = sequenceDictionary;
  }

  @Override protected Class<Messages.MergeComplete> getWorkCompleteClass() { return Messages.MergeComplete.class; }

  @Override
  protected void write(Messages.MergeComplete message) {
    writer.write(message.methylationCalls);
  }

  @Override
  protected void writeHeader(Messages.MergeComplete message) {
    writer.writeHeader(sequenceDictionary);
  }

  @Override
  public void postStop() throws Exception {
    super.postStop();

    writer.close();
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
