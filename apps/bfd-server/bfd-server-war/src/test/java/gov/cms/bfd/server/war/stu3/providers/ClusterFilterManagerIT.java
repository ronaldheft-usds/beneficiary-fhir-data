package gov.cms.bfd.server.war.stu3.providers;

import com.codahale.metrics.MetricRegistry;
import gov.cms.bfd.model.rif.Cluster;
import gov.cms.bfd.model.rif.RifFileEvent;
import gov.cms.bfd.model.rif.RifFileRecords;
import gov.cms.bfd.model.rif.RifFilesEvent;
import gov.cms.bfd.model.rif.samples.StaticRifResource;
import gov.cms.bfd.model.rif.samples.StaticRifResourceGroup;
import gov.cms.bfd.pipeline.rif.extract.RifFilesProcessor;
import gov.cms.bfd.pipeline.rif.load.LoadAppOptions;
import gov.cms.bfd.pipeline.rif.load.RifLoader;
import gov.cms.bfd.pipeline.rif.load.RifLoaderTestUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration tests for {@link gov.cms.bfd.server.war.stu3.providers.ClusterFilterManager}. */
public final class ClusterFilterManagerIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterFilterManagerIT.class);

  @Test
  public void createClusterFilters() {
    loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
    RifLoaderTestUtils.doTestDb(
        entityManager -> {
          ClusterFilterManager filterManager = new ClusterFilterManager();
          filterManager.setEntityManager(entityManager);

          // Without a refresh, the manager should have an empty filter list
          List<ClusterFilter> beforeFilters = filterManager.getFilters();
          Assert.assertEquals(0, beforeFilters.size());

          // Refresh the filter list
          filterManager.refreshClusterFilters();

          // Should have one filter
          List<ClusterFilter> afterFilters = filterManager.getFilters();
          Assert.assertEquals(1, afterFilters.size());
        });
  }

  @Test
  public void testClusterFiltersWithUpdates() {
    loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
    RifLoaderTestUtils.doTestDb(
        entityManager -> {
          ClusterFilterManager filterManager = new ClusterFilterManager();
          filterManager.setEntityManager(entityManager);
          filterManager.refreshClusterFilters();

          // Should have one filter
          List<ClusterFilter> sampleAFilters = filterManager.getFilters();
          Assert.assertEquals(1, sampleAFilters.size());

          // Should have still have one filter after a refresh
          entityManager.clear();
          filterManager.refreshClusterFilters();
          List<ClusterFilter> sampleAFilters2 = filterManager.getFilters();
          Assert.assertEquals(
              sampleAFilters.get(0).getLastUpdated(), sampleAFilters2.get(0).getLastUpdated());

          // Process another set of files to
          loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_U.getResources()));
          entityManager.clear(); // Need to flush the em's cache
          filterManager.refreshClusterFilters();

          // The cluster should be larger. Should have the a filter with a longer time span
          List<ClusterFilter> sampleAUFilters = filterManager.getFilters();
          Assert.assertEquals(1, sampleAUFilters.size());
          Assert.assertEquals(
              sampleAFilters.get(0).getClusterId(), sampleAUFilters.get(0).getClusterId());
          Assert.assertEquals(
              sampleAFilters.get(0).getFirstUpdated(), sampleAUFilters.get(0).getFirstUpdated());
          Assert.assertTrue(
              sampleAFilters
                  .get(0)
                  .getLastUpdated()
                  .before(sampleAUFilters.get(0).getLastUpdated()));
        });
  }

  @Test
  public void testMultipleClusterFilters() {
    loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
    RifLoaderTestUtils.doTestDb(
        entityManager -> {
          ClusterFilterManager filterManager = new ClusterFilterManager();
          filterManager.setEntityManager(entityManager);
          filterManager.refreshClusterFilters();

          // Setup a cluster with an old date
          entityManager.getTransaction().begin();
          List<Cluster> clusters = RifLoaderTestUtils.findClusters(entityManager);
          clusters.get(0).setFirstUpdated(Date.from(Instant.now().minus(49, ChronoUnit.HOURS)));
          clusters.get(0).setLastUpdated(Date.from(Instant.now().minus(48, ChronoUnit.HOURS)));
          entityManager.getTransaction().commit();
          entityManager.clear();
          filterManager.refreshClusterFilters();

          // Process another set of files to create another cluster
          loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_U.getResources()));
          filterManager.refreshClusterFilters();

          // There should be two filters
          List<ClusterFilter> sampleAUFilters = filterManager.getFilters();
          Assert.assertEquals(2, sampleAUFilters.size());
        });
  }

  /**
   * Ensures that {@link gov.cms.bfd.pipeline.rif.load.RifLoaderTestUtils#cleanDatabaseServer} is
   * called after each test case.
   */
  @After
  public void cleanDatabaseServerAfterEachTestCase() {
    RifLoaderTestUtils.cleanDatabaseServer(RifLoaderTestUtils.getLoadOptions());
  }

  /** @param sampleResources the sample RIF resources to load */
  public static void loadData(List<StaticRifResource> sampleResources) {
    LoadAppOptions loadOptions = RifLoaderTestUtils.getLoadOptions();
    RifFilesEvent rifFilesEvent =
        new RifFilesEvent(
            Instant.now(),
            sampleResources.stream().map(r -> r.toRifFile()).collect(Collectors.toList()));

    // Create the processors that will handle each stage of the pipeline.
    MetricRegistry loadAppMetrics = new MetricRegistry();
    RifFilesProcessor processor = new RifFilesProcessor();
    RifLoader loader = new RifLoader(loadAppMetrics, loadOptions);

    // Link up the pipeline and run it.
    for (RifFileEvent rifFileEvent : rifFilesEvent.getFileEvents()) {
      RifFileRecords rifFileRecords = processor.produceRecords(rifFileEvent);
      loader.process(rifFileRecords, error -> {}, result -> {});
    }
  }
}
