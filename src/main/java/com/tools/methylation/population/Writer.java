package com.tools.methylation.population;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.base.Joiner;
import com.tools.actors.AbstractWriter;
import com.tools.io.SequenceDictionary;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;

class Writer extends AbstractWriter<Messages.CallingComplete> {
  private final Joiner joiner = Joiner.on("\t");
  private final DecimalFormat decimalFormat = new DecimalFormat("#.####");
  private final PrintWriter writer;
  private final SequenceDictionary sequenceDictionary;

  public Writer(Path outputPath, SequenceDictionary sequenceDictionary, ActorRef masterRef) throws IOException {
    super(masterRef, true);
    this.sequenceDictionary = sequenceDictionary;
    this.writer = new PrintWriter(Files.newOutputStream(outputPath));
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
    for (Caller.PopulationRatio populationRatio : message.calls) {
      String line = joiner.join(
        populationRatio.contig,
        populationRatio.position,
        populationRatio.strand,
        decimalFormat.format(populationRatio.ratio),
        decimalFormat.format(populationRatio.standardDeviation)
      );
      writer.println(line);
    }
  }

  @Override
  protected void writeHeader(Messages.CallingComplete message) {
    writer.println("##methylprf");

    for (String contig : sequenceDictionary.getSortedContigs()) {
      String line = joiner.join("#seq", contig, sequenceDictionary.contigLengthMap.get(contig));
      writer.println(line);
    }

    // Write the column headers
    String line = joiner.join("#Contig", "Position", "Strand", "Ratio", "Standard Deviation");
    writer.println(line);
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
