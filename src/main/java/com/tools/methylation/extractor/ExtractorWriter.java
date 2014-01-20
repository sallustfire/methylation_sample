package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.google.common.collect.ImmutableMap;
import com.tools.actors.AbstractWriter;
import com.tools.io.MethylationCall;
import com.tools.io.MethylationCallWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class ExtractorWriter extends AbstractWriter<Messages.MethylationCalculated> {
  private final Path cpgOutputPath;
  private final Path chgOutputPath;
  private final Path chhOutputPath;
  private final Path cnOutputPath;
  private final MethylationCounts counts;

  // Counts stats about the depth of coverage across each contig
  private final HashMap<String, DepthCounter> depthCounts = new HashMap<>();

  private boolean written = false;

  public ExtractorWriter(Path cpgOutputPath,
                         Path chgOutputPath,
                         Path chhOutputPath,
                         Path cnOutputPath,
                         CoordinateConverter coordinateConverter,
                         Map<Character, ArrayList<MethylationCall>> remainders,
                         ActorRef masterRef) throws IOException {
    super(masterRef, true);
    this.cpgOutputPath = cpgOutputPath;
    this.chgOutputPath = chgOutputPath;
    this.chhOutputPath = chhOutputPath;
    this.cnOutputPath = cnOutputPath;
    this.counts = new MethylationCounts(coordinateConverter);
    counts.countAll(remainders);
  }

  @Override
  protected Class<Messages.MethylationCalculated> getWorkCompleteClass() {
    return Messages.MethylationCalculated.class;
  }

  @Override
  protected void handleCustom(Object message) throws Exception {
    if (message instanceof Messages.WriteAll && !written) {
      // Write everything to file
      written = true;
      Map<Character, ArrayList<MethylationCall>> remainders = writeAll();

      // Inform the master that all writing in now complete
      masterRef.tell(new Messages.WriteAllComplete(remainders, depthCounts), getSelf());
    } else unhandled(message);
  }

  @Override
  protected void write(Messages.MethylationCalculated message) {
    // Merge the counts
    this.counts.countAll(message.counts);
  }

  @Override
  protected void writeHeader(Messages.MethylationCalculated message) { }

  private Map<Character, ArrayList<MethylationCall>> writeAll() throws IOException {
    // Write out each of the contexts
    ArrayList<MethylationCall> cpgRemainders = writeContext(MethylationCounts.CPG_CONTEXT, cpgOutputPath);
    ArrayList<MethylationCall> chgRemainders = writeContext(MethylationCounts.CHG_CONTEXT, chgOutputPath);
    ArrayList<MethylationCall> chhRemainders = writeContext(MethylationCounts.CHH_CONTEXT, chhOutputPath);
    ArrayList<MethylationCall> cnRemainders = writeContext(MethylationCounts.CN_CHN_CONTEXT, cnOutputPath);

    return ImmutableMap.of(
      MethylationCounts.CPG_CONTEXT, cpgRemainders,
      MethylationCounts.CHG_CONTEXT, chgRemainders,
      MethylationCounts.CHH_CONTEXT, chhRemainders,
      MethylationCounts.CN_CHN_CONTEXT, cnRemainders
    );
  }

  private ArrayList<MethylationCall> writeContext(char context, Path outputPath) throws IOException {
    ArrayList<MethylationCall> remainders;
    try (OutputStream outputStream = Files.newOutputStream(outputPath, StandardOpenOption.APPEND);
         MethylationCallWriter writer = new MethylationCallWriter(outputStream)) {
      MethylationCounts.MethylationCallIterator methylationCalls = counts.iterator(context);

      // Write all of the counts
      String contig = null;
      DepthCounter contigDepthCounter = null;
      while (methylationCalls.hasNext()) {
        MethylationCall methylationCall = methylationCalls.next();

        // Count the site
        if (!methylationCall.contig.equals(contig)) {
          contig = methylationCall.contig;
          if (depthCounts.containsKey(contig)) contigDepthCounter = depthCounts.get(contig);
          else {
            contigDepthCounter = new DepthCounter();
            depthCounts.put(contig, contigDepthCounter);
          }
        }
        contigDepthCounter.count(methylationCall.methylatedCount, methylationCall.totalCount);

        writer.write(methylationCall);
      }

      remainders = methylationCalls.getRemainingCounts();
    }

    return remainders;
  }

  public static Props props(final Path cpgOutputPath,
                            final Path chgOutputPath,
                            final Path chhOutputPath,
                            final Path cnOutputPath,
                            final CoordinateConverter coordinateConverter,
                            final Map<Character, ArrayList<MethylationCall>> remainders,
                            final ActorRef receiverRef) {
    return Props.create(new Creator<ExtractorWriter>() {
      @Override
      public ExtractorWriter create() throws Exception {
        return new ExtractorWriter(
          cpgOutputPath,
          chgOutputPath,
          chhOutputPath,
          cnOutputPath,
          coordinateConverter,
          remainders,
          receiverRef
        );
      }
    });
  }
}
