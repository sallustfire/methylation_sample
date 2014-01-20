package com.tools.io;

import net.sf.samtools.SAMSequenceRecord;

import java.util.*;

/**
 * Stores the lengths and order of a sequence of contigs.
 */
public class SequenceDictionary {
  // A Map giving the length of each contig
  public final LinkedHashMap<String, Integer> contigLengthMap;

  // A Map giving the order of each contig
  public final HashMap<String, Integer> contigOrderMap;

  // Collection enumerating the contigs used as control sequences to test for false positives
  public final List<String> controlContigs;

  public SequenceDictionary(LinkedHashMap<String, Integer> contigLengthMap) {
    this(contigLengthMap, new ArrayList<String>());
  }

  public SequenceDictionary(LinkedHashMap<String, Integer> contigLengthMap, List<String> controlContigs) {
    this.contigLengthMap = contigLengthMap;
    this.controlContigs = controlContigs;
    this.contigOrderMap = buildContigOrderMap(contigLengthMap, controlContigs);
  }

  /**
   * Returns the index of the specified contig in the dictionary.
   *
   * @param contigName  the String name of the contig
   * @return a int indicatig the 0-based position of the contig in the sorted collection of contigs
   */
  public int getContigIndex(String contigName) {
    Integer index = contigOrderMap.get(contigName);
    if (index == null) throw new NoSuchElementException(contigName + " is not defined");

    return index;
  }

  /**
   * Returns a Comparator<String> that sorts contigs identifiers using the sequence dictionary order.
   */
  public Comparator<String> getContigOrder() {
    return new Comparator<String>() {
      @Override
      public int compare(String contig1, String contig2) {
        return contigOrderMap.get(contig1) - contigOrderMap.get(contig2);
      }
    };
  }

  /**
   * Returns an ArrayList<String> of the contig names in the sorted order.
   */
  public ArrayList<String> getSortedContigs() {
    ArrayList<String> sortedContigs = new ArrayList<>(contigOrderMap.keySet());
    Collections.sort(sortedContigs, getContigOrder());

    return sortedContigs;
  }

  private static HashMap<String, Integer> buildContigOrderMap(LinkedHashMap<String, Integer> contigLengthMap,
                                                              List<String> controlContigs) {
    // Determine the sort order with control contigs in the front
    final HashSet<String> controlContigsSet = new HashSet<>(controlContigs);
    Comparator<String> contigComparator = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        int comparison;
        if (controlContigsSet.contains(o1) && !controlContigsSet.contains(o2)) comparison = -1;
        else if (controlContigsSet.contains(o1) == controlContigsSet.contains(o2)) comparison = 0;
        else comparison = 1;

        return comparison;
      }
    };

    // Ensure the collection is sorted
    ArrayList<String> sortedContigs = new ArrayList<>(contigLengthMap.keySet());
    Collections.sort(sortedContigs, contigComparator);

    HashMap<String, Integer> contigOrderMap = new HashMap<>();
    Iterator<String> contigs = sortedContigs.iterator();
    for (int i = 0; contigs.hasNext(); i++) contigOrderMap.put(contigs.next(), i);

    return contigOrderMap;
  }
}
