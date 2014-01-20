package com.tools.methylation.differential;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.google.common.base.Joiner;
import com.tools.actors.AbstractWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

class Writer extends AbstractWriter<Messages.CallingComplete> {
  private final PrintWriter writer;
  private final boolean isRegionFormat;

  public Writer(Path outputPath, boolean isRegionFormat, ActorRef masterRef) throws IOException {
    super(masterRef, true);
    this.writer = new PrintWriter(Files.newOutputStream(outputPath));
    this.isRegionFormat = isRegionFormat;
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
    Joiner joiner = Joiner.on("\t");
    for (DifferentialCall call : message.differentialCalls) {
      String line = createCallString(call, joiner);
      writer.println(line);
    }
  }

  @Override
  protected void writeHeader(Messages.CallingComplete message) {
    String line;
    if (isRegionFormat) line = "Id\tContig\tStart\tStop\tSample 1 Mean\tSample 2 Mean\tT Statistic\tP-value";
    else line = "Contig\tPosition\tStrand\tSample 1 Mean\tSample 2 Mean\tT Statistic\tP-value";

    writer.println(line);
  }

  private String createCallString(DifferentialCall differentialCall, Joiner joiner) {
    String result;
    if (differentialCall instanceof DifferentialSiteCall) {
      DifferentialSiteCall call = (DifferentialSiteCall) differentialCall;
      result = joiner.join(
        call.contig,
        call.position,
        call.strand,
        String.format("%.3g", call.sample1Mean),
        String.format("%.3g", call.sample2Mean),
        String.format("%.3g", call.tStatistic),
        call.pValue
      );
    } else if (differentialCall instanceof DifferentialRegionCall) {
      DifferentialRegionCall call = (DifferentialRegionCall) differentialCall;
      result = joiner.join(
        call.id,
        call.contig,
        call.start,
        call.stop,
        String.format("%.3g", call.sample1Mean),
        String.format("%.3g", call.sample2Mean),
        String.format("%.3g", call.tStatistic),
        call.pValue
      );
    } else if (differentialCall instanceof DifferentialCallMissing) {
      DifferentialCallMissing call = (DifferentialCallMissing) differentialCall;
      result = joiner.join(
        call.id,
        call.contig,
        call.start,
        call.stop,
        "NA",
        "NA",
        "NA",
        "NA"
      );
    } else throw new RuntimeException("Unknown differential call encountered");

    return result;
  }

  public static Props props(final Path outputPath, final boolean isRegionFormat, final ActorRef receiverRef) {
    return Props.create(new Creator<Writer>() {
      @Override
      public Writer create() throws Exception {
        return new Writer(outputPath, isRegionFormat, receiverRef);
      }
    });
  }
}
