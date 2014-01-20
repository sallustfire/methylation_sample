package com.tools.methylation.utils;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import com.tools.ApplicationTest;
import com.tools.methylation.utils.CallRegions;
import com.tools.methylation.utils.Region;
import com.tools.methylation.utils.RegionReader;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class RegionReaderTest extends ApplicationTest {
  static ActorSystem system = ActorSystem.create("TestSys");

  @Test
  public void testRead() throws Exception {
    List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/VK1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK3Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV3Dconv.CpG_context.tab")
    );

    final List<Region> regions = Arrays.asList(
      new Region("Malat1", "19", 5795690, 5802671),
      new Region("1", "2", 3051244, 3054244),
      new Region("2", "2", 5795690, 5802671)
    );

    JavaTestKit testKit = new JavaTestKit(system);
    Props props = RegionReader.props(inputPaths, regions, 1000, testKit.getTestActor());
    TestActorRef<RegionReader> actorRef = TestActorRef.create(system, props);
    RegionReader actor = actorRef.underlyingActor();

    CallRegions callRegions = (CallRegions) actor.read(0);
    Assert.assertEquals(3, callRegions.regionCalls.size());

    Assert.assertEquals(0, callRegions.regionCalls.get(1).calls.get(0).size());
    Assert.assertEquals("19", callRegions.regionCalls.get(2).region.contig);
  }

  @Test
  public void testReadOverlapping() throws Exception {
    List<Path> inputPaths = Arrays.asList(
      getFileResource("/methylation/VK1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VK3Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV1Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV2Dconv.CpG_context.tab"),
      getFileResource("/methylation/VV3Dconv.CpG_context.tab")
    );

    final List<Region> regions = Arrays.asList(
      new Region("1", "2", 3051244, 3054244),
      new Region("1", "2", 3051244, 3054244)
    );

    JavaTestKit testKit = new JavaTestKit(system);
    Props props = RegionReader.props(inputPaths, regions, 1000, testKit.getTestActor());
    TestActorRef<RegionReader> actorRef = TestActorRef.create(system, props);
    RegionReader actor = actorRef.underlyingActor();

    CallRegions callRegions = (CallRegions) actor.read(0);
    Assert.assertEquals(2, callRegions.regionCalls.size());

    Assert.assertEquals(33, callRegions.regionCalls.get(0).calls.get(0).size());
    Assert.assertEquals(33, callRegions.regionCalls.get(1).calls.get(0).size());
  }
}
