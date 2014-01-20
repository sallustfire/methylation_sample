package com.tools;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.tools.methylation.caller.MethylationRatioCaller;
import com.tools.methylation.differential.DifferentialMethylationCaller;
import com.tools.methylation.extractor.BismarkMethylationExtractor;
import com.tools.methylation.merger.MethylationCallMerger;
import com.tools.methylation.population.MethylationPopulationCaller;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public class Runner {

  public static void main(String[] args) throws Exception {
    if (args.length > 0) {
      String[] trailingArgs = Arrays.copyOfRange(args, 1, args.length);
      switch (args[0]) {
        case "call":
          runCaller(trailingArgs);
          break;
        case "extract":
          runExtractor(trailingArgs);
          break;
        case "merge":
          runMerger(trailingArgs);
          break;
        case "popCall":
          runPopulationCaller(trailingArgs);
          break;
        case "diff":
          runDifferentialCaller(trailingArgs);
          break;
        default: displayHelp();
      }
    } else displayHelp();
  }

  public static void runCaller(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    ValueConverter<Path> PathConverter = new PathConverter();
    OptionSpec<Path> input =
      parser.accepts("input", "input methylation call file").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Path> output =
      parser.accepts("output", "output methylation call file").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Integer> threads = parser.accepts("threads", "maximum number of threads")
      .withOptionalArg()
      .ofType(Integer.class)
      .defaultsTo(1);
    OptionSpec<Double> error = parser.accepts("error", "default probability methylation call is false positive")
      .withOptionalArg()
      .ofType(Double.class);

    // Configure help screen
    parser.accepts("help").forHelp();

    OptionSet options = parser.parse(args);

    if (options.hasArgument(input) && options.hasArgument(output)) {
      Optional<Double> defaultErrorRate = Optional.fromNullable(options.valueOf(error));

      MethylationRatioCaller caller = new MethylationRatioCaller(
        options.valueOf(input),
        options.valueOf(output),
        defaultErrorRate
      );
      caller.run(options.valueOf(threads));
    } else parser.printHelpOn(System.out);
  }

  public static void runPopulationCaller(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    ValueConverter<Path> PathConverter = new PathConverter();
    OptionSpec<Path> input =
      parser.accepts("inputs", "input methylation call files").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Path> output =
      parser.accepts("output", "output methylation call file").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Integer> threads = parser.accepts("threads", "maximum number of threads")
      .withOptionalArg()
      .ofType(Integer.class)
      .defaultsTo(1);
    OptionSpec<Integer> error = parser.accepts("cutoff", "minimum depth required to use a call from a sample")
      .withOptionalArg()
      .ofType(Integer.class);

    // Configure help screen
    parser.accepts("help").forHelp();

    OptionSet options = parser.parse(args);

    if (options.hasArgument(input) && options.hasArgument(output)) {
      Optional<Integer> cutoff = Optional.fromNullable(options.valueOf(error));

      MethylationPopulationCaller caller = new MethylationPopulationCaller(
        options.valuesOf(input),
        options.valueOf(output),
        cutoff
      );
      caller.run(options.valueOf(threads));
    } else parser.printHelpOn(System.out);
  }

  public static void runDifferentialCaller(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    ValueConverter<Path> PathConverter = new PathConverter();
    OptionSpec<Path> input =
      parser.accepts("input", "input methylation call files").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Path> output =
      parser.accepts("output", "output methylation call file").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<String> conditions = parser.accepts("conditions", "input condition assignments").withRequiredArg();
    OptionSpec<Path> regions = parser
      .accepts("regions", "tab delimited file specifying regions for testing")
      .withOptionalArg()
      .withValuesConvertedBy(PathConverter);
    OptionSpec<Integer> threads = parser.accepts("threads", "maximum number of threads")
      .withOptionalArg()
      .ofType(Integer.class)
      .defaultsTo(1);

    // Configure help screen
    parser.accepts("help").forHelp();

    OptionSet options = parser.parse(args);

    if (options.hasArgument(input) && options.hasArgument(output) && options.hasArgument(conditions)) {
      ArrayList<Integer> conditionValues = new ArrayList<>();
      Splitter splitter = Splitter.on(",");
      for (String stringValue : splitter.splitToList(options.valueOf(conditions))) {
        conditionValues.add(Integer.parseInt(stringValue));
      }

      Optional<Path> regionPath;
      if (options.hasArgument(regions)) regionPath = Optional.of(options.valueOf(regions));
      else regionPath = Optional.absent();

      DifferentialMethylationCaller caller = new DifferentialMethylationCaller(
        options.valuesOf(input),
        conditionValues,
        options.valueOf(output),
        regionPath
      );
      caller.run(options.valueOf(threads));
    } else parser.printHelpOn(System.out);
  }

  public static void runExtractor(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    ValueConverter<Path> PathConverter = new PathConverter();
    OptionSpec<Path> input =
      parser.accepts("input", "input bismark alignment file").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Path> output =
      parser.accepts("output", "prefix for output files").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<String> controlContigs = parser.accepts("contigs", "contigs that biologically have no methylation")
      .withOptionalArg()
      .ofType(String.class)
      .withValuesSeparatedBy(' ');
    OptionSpec<Integer> threads = parser.accepts("threads", "maximum number of threads")
      .withOptionalArg()
      .ofType(Integer.class)
      .defaultsTo(1);

    // Configure help screen
    parser.accepts("help").forHelp();

    OptionSet options = parser.parse(args);

    if (options.hasArgument(input) && options.hasArgument(output)) {
      BismarkMethylationExtractor extractor = new BismarkMethylationExtractor(
        options.valueOf(input),
        options.valueOf(output),
        options.valuesOf(controlContigs)
      );
      extractor.run(options.valueOf(threads));
    } else parser.printHelpOn(System.out);
  }

  public static void runMerger(String[] args) throws Exception {
    OptionParser parser = new OptionParser();
    ValueConverter<Path> PathConverter = new PathConverter();
    OptionSpec<Path> input =
      parser.accepts("input", "input methylation call files").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Path> output =
      parser.accepts("output", "output methylation call file").withRequiredArg().withValuesConvertedBy(PathConverter);
    OptionSpec<Integer> threads = parser.accepts("threads", "maximum number of threads")
      .withOptionalArg()
      .ofType(Integer.class)
      .defaultsTo(1);

    // Configure help screen
    parser.accepts("help").forHelp();

    OptionSet options = parser.parse(args);

    if (options.hasArgument(input) && options.hasArgument(output)) {
      MethylationCallMerger merger = new MethylationCallMerger(
        options.valuesOf(input),
        options.valueOf(output)
      );
      merger.run(options.valueOf(threads));
    } else parser.printHelpOn(System.out);
  }

  private static void displayHelp() {
    System.out.println("Program: tools");
    System.out.println("Version: 0.1");
    System.out.println();
    System.out.println("Usage:   tools <command> [options]");
    System.out.println();
    System.out.println("Command:");
    System.out.println("  call     Make consensus methylation calls at context sites");
    System.out.println("  diff     Identify differentially methylated sites between two conditions");
    System.out.println("  extract  Extract methylation calls from a bismark alignment file");
    System.out.println("  merge    Merge methylation calls from multiple call files");
    System.out.println("  popCall  Make consensus methylation calls across biological replicates");
    System.out.println();
  }

  private static class PathConverter implements ValueConverter<Path> {
    public Path convert(String value) {
      return Paths.get(value);
    }

    public Class<Path> valueType() {
      return Path.class;
    }

    public String valuePattern() {
      return null;
    }
  }
}
