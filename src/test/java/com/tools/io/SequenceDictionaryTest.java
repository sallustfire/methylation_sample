package com.tools.io;

import com.google.common.collect.ImmutableMap;
import com.tools.ApplicationTest;
import org.javatuples.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SequenceDictionaryTest extends ApplicationTest {

  @Test
  public void testGetSortedContigs() throws Exception {
    Map<String, Integer> contigLengths = ImmutableMap.of(
      "Chr1", 30427671,
      "Chr2", 19698289,
      "Chr3", 23459830,
      "Chr4", 18585056
    );

    List<Pair<List<String>, List<String>>> expectations = Arrays.asList(
      Pair.with(Arrays.<String>asList(), Arrays.asList("Chr1", "Chr2", "Chr3", "Chr4")),
      Pair.with(Arrays.asList("Chr4"), Arrays.asList("Chr4", "Chr1", "Chr2", "Chr3")),
      Pair.with(Arrays.asList("Chr2", "Chr3"), Arrays.asList("Chr2", "Chr3", "Chr1", "Chr4")),
      Pair.with(Arrays.asList("Chr3", "Chr2"), Arrays.asList("Chr2", "Chr3", "Chr1", "Chr4"))
    );

    for (Pair<List<String>, List<String>> pair : expectations) {
      SequenceDictionary dictionary = new SequenceDictionary(new LinkedHashMap<>(contigLengths), pair.getValue0());
      Assert.assertEquals(pair.getValue1(), dictionary.getSortedContigs());
    }
  }
}