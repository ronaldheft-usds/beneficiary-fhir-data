package gov.cms.bfd.server.war.stu3.providers;

import com.codahale.metrics.MetricRegistry;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration tests for {@link gov.cms.bfd.server.war.stu3.providers.LoadedFilterManager}. */
public final class LoadedFilterManagerIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadedFilterManagerIT.class);
  private static final String SAMPLE_BENE = "567834";
  private static final String INVALID_BENE = "1";

  @Test
  public void refreshFilters() {
    RifLoaderTestUtils.doTestWithDb(
        entityManager -> {
          LoadedFilterManager filterManager = new LoadedFilterManager();
          filterManager.setEntityManager(entityManager);
          loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

          // Without a refresh, the manager should have an empty filter list
          List<LoadedFileFilter> beforeFilters = filterManager.getFilters();
          Assert.assertEquals(0, beforeFilters.size());

          // Refresh the filter list
          filterManager.refreshClusterFilters();

          // Should have many filters
          List<LoadedFileFilter> afterFilters = filterManager.getFilters();
          Assert.assertTrue(afterFilters.size() > 1);

          // Should be sorted by lastUpdated date
          LoadedFileFilter firstFilter = afterFilters.get(0);
          LoadedFileFilter lastFilter = afterFilters.get(afterFilters.size() - 1);
          Assert.assertTrue(
              lastFilter.getLastUpdated().getTime() <= firstFilter.getLastUpdated().getTime());
        });
  }

  /** Test refreshFilters when updating */
  @Test
  public void refreshFiltersWithUpdates() {

    RifLoaderTestUtils.doTestWithDb(
        entityManager -> {
          LoadedFilterManager filterManager = new LoadedFilterManager();
          filterManager.setEntityManager(entityManager);
          filterManager.refreshClusterFilters();

          // Should have many filters
          List<LoadedFileFilter> sampleAFilters = filterManager.getFilters();
          Assert.assertTrue(sampleAFilters.size() > 1);
          LoadedFileFilter aFilter = sampleAFilters.get(0);

          // Should have the same filters
          filterManager.refreshFiltersWithDelay(0);
          List<LoadedFileFilter> sampleAFilters2 = filterManager.getFilters();
          LoadedFileFilter a2Filter = sampleAFilters2.get(0);
          Assert.assertEquals(sampleAFilters.size(), sampleAFilters2.size());
          Assert.assertEquals(aFilter.getFileId(), a2Filter.getFileId());
          Assert.assertEquals(aFilter.getLastUpdated(), a2Filter.getLastUpdated());

          // Process another set of files
          pause(100);
          loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_U.getResources()));
          filterManager.refreshFiltersWithDelay(0);

          // The filter set should be larger. Should have the a filter with a longer time span
          List<LoadedFileFilter> sampleAUFilters = filterManager.getFilters();
          LoadedFileFilter auFilter = sampleAUFilters.get(0);
          Assert.assertTrue(sampleAFilters.size() < sampleAUFilters.size());
          Assert.assertTrue(
              aFilter.getLastUpdated().getTime() <= auFilter.getLastUpdated().getTime());
        });
  }

  /** Test isResultSetEmpty with one and two filters */
  @Test
  public void isResultSetEmpty() {
    RifLoaderTestUtils.doTestWithDb(
        entityManager -> {
          LoadedFilterManager filterManager = new LoadedFilterManager();
          filterManager.setEntityManager(entityManager);
          loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

          // Establish a couple of times
          RifLoaderTestUtils.pauseMillis(1000);
          Date afterSampleA = Date.from(Instant.now());
          RifLoaderTestUtils.pauseMillis(10);
          Date afterSampleAPlus = Date.from(Instant.now());
          DateRangeParam afterSampleARange = new DateRangeParam(afterSampleA, afterSampleAPlus);

          // Refresh the filter list
          RifLoaderTestUtils.pauseMillis(10);
          filterManager.refreshFiltersWithDelay(0);

          // Test after refresh
          Assert.assertTrue(
              "Expected date range to not have a matching filter",
              filterManager.isResultSetEmpty(SAMPLE_BENE, afterSampleARange));
          Assert.assertTrue(
              "Expected date range to not have a matching filter",
              filterManager.isResultSetEmpty(INVALID_BENE, afterSampleARange));
        });
  }

  /** @param sampleResources the sample RIF resources to load */
  private static void loadData(List<StaticRifResource> sampleResources) {
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

  /**
   * Pause execution to allow time to pass.
   *
   * @param millis to pause for
   */
  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {

    }
  }
}
