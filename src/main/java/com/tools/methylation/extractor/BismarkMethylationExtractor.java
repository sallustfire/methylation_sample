package com.tools.methylation.extractor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.tools.io.MethylationCallWriter;
import com.tools.io.SequenceDictionary;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMSequenceDictionary;
import net.sf.samtools.SAMSequenceRecord;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;

public class BismarkMethylationExtractor {
  private final Logger logger = LoggerFactory.getLogger(BismarkMethylationExtractor.class);

  private final Path inputPath;
  private final Path outputDirectory;
  private final List<String> controlContigs;

  public BismarkMethylationExtractor(Path inputPath, Path outputDirectory, List<String> controlContigs) {
    this.inputPath = inputPath;
    this.outputDirectory = outputDirectory;
    this.controlContigs = controlContigs;
  }

  public void run(int threadCount) throws Exception {
    // Create the output paths
    Files.createDirectories(outputDirectory.toAbsolutePath());
    Path cpgOutputPath = outputDirectory.resolve("CpG_context.tab");
    Path chgOutputPath = outputDirectory.resolve("CHG_context.tab");
    Path chhOutputPath = outputDirectory.resolve("CHH_context.tab");
    Path cnOutputPath = outputDirectory.resolve("CN_CHN_context.tab");
    Path summaryPath = outputDirectory.resolve("summary.tab");

    // Write out the headers
    SequenceDictionary sequenceDictionary = writeHeaders(
      inputPath,
      controlContigs,
      cpgOutputPath,
      chgOutputPath,
      chhOutputPath,
      cnOutputPath
    );

    // Determine the maximum amount of memory that can be used in bytes
    long availableMemory = Runtime.getRuntime().maxMemory() / 2;
    logger.info("Using {} of memory", FileUtils.byteCountToDisplaySize(availableMemory));

    Props props = Master.props(
      inputPath,
      sequenceDictionary,
      cpgOutputPath,
      chgOutputPath,
      chhOutputPath,
      cnOutputPath,
      summaryPath,
      availableMemory,
      threadCount
    );
    runActors(props);
  }

  private void runActors(Props masterProps) {
    // Create the thread system
    Config config = ConfigFactory.parseString("akka {log-dead-letters = 0}");
    ActorSystem system = ActorSystem.create("MethylationSystem", config);
    ActorRef master = system.actorOf(masterProps);

    // Start
    master.tell(new Messages.Start(), master);
    system.awaitTermination();
  }


  private SequenceDictionary writeHeaders(Path samPath, List<String> controlContigs, Path... paths) throws IOException {
    final SAMSequenceDictionary samSequenceDictionary;
    try (SAMFileReader samReader = new SAMFileReader(samPath.toFile())) {
      samSequenceDictionary = samReader.getFileHeader().getSequenceDictionary();
    }

    // Create the sequence dictionary
    LinkedHashMap<String, Integer> contigLengths = new LinkedHashMap<>();
    for (SAMSequenceRecord sequenceRecord : samSequenceDictionary.getSequences()) {
      contigLengths.put(sequenceRecord.getSequenceName(), sequenceRecord.getSequenceLength());
    }
    SequenceDictionary sequenceDictionary = new SequenceDictionary(contigLengths, controlContigs);

    for (Path path : paths) {
      try (OutputStream outputStream = Files.newOutputStream(path);
           MethylationCallWriter writer = new MethylationCallWriter(outputStream)) {
        writer.writeHeader(sequenceDictionary);
      }
    }

    return sequenceDictionary;
  }
}
