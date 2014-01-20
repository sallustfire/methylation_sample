package com.tools.methylation.population;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Creator;
import com.tools.actors.AbstractWorker;
import com.tools.io.MethylationCall;
import com.tools.io.SequenceDictionary;

import java.util.*;

class Caller extends AbstractWorker<Messages.CallsRead, Messages.CallingComplete> {
  private final SequenceDictionary sequenceDictionary;


  public Caller(SequenceDictionary sequenceDictionary, ActorRef writerRef) {
    super(writerRef);
    this.sequenceDictionary = sequenceDictionary;
  }

  @Override protected Class<Messages.CallsRead> getWorkClass() { return Messages.CallsRead.class; }

  @Override
  protected Messages.CallingComplete work(Messages.CallsRead message) {
    ArrayList<PopulationRatio> consensusCalls = call(message.blocks, sequenceDictionary);
    return new Messages.CallingComplete(message.index, consensusCalls);
  }

  /**
   * Returns an ArrayList of MethylationCalls built out of merging co-located counts across all the blocks.
   *
   * @param callBlocks          the List<ArrayDeque<MethylationCall>> enumerating the calls across all of the blocks
   * @param sequenceDictionary  the SequenceDictionary specifying the order of the contigs
   *
   * @return an ArrayList<MethylationCall> with the merged calls in the contig order
   */
  protected ArrayList<PopulationRatio> call(List<ArrayDeque<MethylationCall>> callBlocks,
                                            SequenceDictionary sequenceDictionary) {
    HashMap<String, TreeMap<Integer, Observations>> mergedCalls = new HashMap<>();
    for (ArrayDeque<MethylationCall> callBlock : callBlocks) {
      for (MethylationCall call : callBlock) {
        // Retrieve the contig call count structure
        TreeMap<Integer, Observations> contigObservations = mergedCalls.get(call.contig);
        if (contigObservations == null) {
          contigObservations = new TreeMap<>();
          mergedCalls.put(call.contig, contigObservations);
        }

        // Increment the counts
        Observations observations = contigObservations.get(call.position);
        if (observations == null) {
          observations = new Observations(call.strand);
          contigObservations.put(call.position, observations);
        }
        observations.count(call.ratio.get());
      }
    }

    ArrayList<String> contigs = new ArrayList<>(mergedCalls.keySet());
    Collections.sort(contigs, sequenceDictionary.getContigOrder());

    ArrayList<PopulationRatio> methylationCalls = new ArrayList<>();
    for (String contig : contigs) {
      for (Map.Entry<Integer, Observations> entry : mergedCalls.get(contig).entrySet()) {
        Observations observations = entry.getValue();

        // If there is sufficient representation, observe
        if (observations.values.size() >= 2) {
          PopulationRatio populationRatio = new PopulationRatio(
            contig,
            entry.getKey(),
            observations.strand,
            observations.mean(),
            observations.meanStandardDeviation()
          );
          methylationCalls.add(populationRatio);
        }
      }
    }

    return methylationCalls;
  }

  public static Props props(final SequenceDictionary sequenceDictionary, final ActorRef writerRef) {
    return Props.create(new Creator<Caller>() {
      @Override
      public Caller create() throws Exception {
        return new Caller(sequenceDictionary, writerRef);
      }
    });
  }

  class Observations {
    private final char strand;
    private final ArrayList<Double> values = new ArrayList<>();

    public Observations(char strand) {
      this.strand = strand;
    }

    public void count(double observation) { values.add(observation); }

    public double mean() {
      double cumulativeValue = 0;
      for (Double value : values) cumulativeValue += value;

      return cumulativeValue / values.size();
    }

    public double standardDeviation() {
      double cumulativeValue = 0;
      double mean = mean();
      for (double value : values) cumulativeValue += Math.pow(value - mean, 2);

      return Math.sqrt(cumulativeValue / (values.size() - 1));
    }

    public double meanStandardDeviation() {
      return standardDeviation() / Math.sqrt(values.size());
    }
  }

  class PopulationRatio {
    public final String contig;
    public final int position;
    public final char strand;
    public final double ratio;
    public final double standardDeviation;

    public PopulationRatio(String contig, int position, char strand, double ratio, double standardDeviation) {
      this.contig = contig;
      this.position = position;
      this.strand = strand;
      this.ratio = ratio;
      this.standardDeviation = standardDeviation;
    }
  }
}
