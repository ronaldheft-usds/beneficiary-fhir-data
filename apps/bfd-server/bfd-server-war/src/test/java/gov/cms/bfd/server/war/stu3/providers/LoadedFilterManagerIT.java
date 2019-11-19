package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
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
import javax.sql.DataSource;
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
        (dataSource, entityManager) -> {
          final LoadedFilterManager filterManager = new LoadedFilterManager();
          filterManager.setEntityManager(entityManager);
          loadData(dataSource, Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

          // Without a refresh, the manager should have an empty filter list
          final List<LoadedFileFilter> beforeFilters = filterManager.getFilters();
          Assert.assertEquals(0, beforeFilters.size());

          // Refresh the filter list
          filterManager.refreshFiltersWithDelay(0);

          // Should have many filters
          final List<LoadedFileFilter> afterFilters = filterManager.getFilters();
          Assert.assertTrue(afterFilters.size() > 1);

          // Should be sorted by lastUpdated date
          final LoadedFileFilter firstFilter = afterFilters.get(0);
          final LoadedFileFilter lastFilter = afterFilters.get(afterFilters.size() - 1);
          Assert.assertTrue(
              lastFilter.getLastUpdated().getTime() <= firstFilter.getLastUpdated().getTime());
        });
  }

  /** Test isResultSetEmpty with one filter */
  @Test
  public void isResultSetEmpty() {
    RifLoaderTestUtils.doTestWithDb(
        (dataSource, entityManager) -> {
          final LoadedFilterManager filterManager = new LoadedFilterManager();
          filterManager.setEntityManager(entityManager);
          loadData(dataSource, Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

          // Establish a couple of times
          RifLoaderTestUtils.pauseMillis(1000);
          final Date afterSampleA = Date.from(Instant.now());
          RifLoaderTestUtils.pauseMillis(10);
          final Date afterSampleAPlus = Date.from(Instant.now());
          final DateRangeParam afterSampleARange =
              new DateRangeParam(afterSampleA, afterSampleAPlus);

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

  /** Test isResultSetEmpty with multiple refreshes */
  @Test
  public void testWithMultipleRefreshes() {
    RifLoaderTestUtils.doTestWithDb(
        (dataSource, entityManager) -> {
          final LoadedFilterManager filterManager = new LoadedFilterManager();
          filterManager.setEntityManager(entityManager);
          loadData(dataSource, Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

          // Establish a couple of times
          RifLoaderTestUtils.pauseMillis(1000);
          final Date afterSampleA = Date.from(Instant.now());
          RifLoaderTestUtils.pauseMillis(10);
          final Date afterSampleAPlus = Date.from(Instant.now());
          final DateRangeParam afterSampleARange =
              new DateRangeParam(afterSampleA, afterSampleAPlus);

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

          loadData(dataSource, Arrays.asList(StaticRifResourceGroup.SAMPLE_U.getResources()));

          // Load again
          RifLoaderTestUtils.pauseMillis(1000);
          final Date afterSampleU = Date.from(Instant.now());
          final DateRangeParam aroundSampleU = new DateRangeParam(afterSampleA, afterSampleU);
          filterManager.refreshFiltersWithDelay(0);

          // Test after refresh
          Assert.assertFalse(
              "Expected date range to not have a matching filter",
              filterManager.isResultSetEmpty(SAMPLE_BENE, aroundSampleU));
          Assert.assertTrue(
              "Expected date range to not have a matching filter",
              filterManager.isResultSetEmpty(INVALID_BENE, aroundSampleU));
        });
  }

  /** @param sampleResources the sample RIF resources to load */
  private static void loadData(DataSource dataSource, List<StaticRifResource> sampleResources) {
    LoadAppOptions loadOptions = RifLoaderTestUtils.getLoadOptions(dataSource);
    RifFilesEvent rifFilesEvent =
        new RifFilesEvent(
            Instant.now(),
            sampleResources.stream().map(r -> r.toRifFile()).collect(Collectors.toList()));

    // Create the processors that will handle each stage of the pipeline.
    MetricRegistry loadAppMetrics = new MetricRegistry();
    RifFilesProcessor processor = new RifFilesProcessor();
    try (final RifLoader loader = new RifLoader(loadAppMetrics, loadOptions)) {
      // Link up the pipeline and run it.
      for (RifFileEvent rifFileEvent : rifFilesEvent.getFileEvents()) {
        RifFileRecords rifFileRecords = processor.produceRecords(rifFileEvent);
        loader.process(rifFileRecords, error -> {}, result -> {});
      }
    }
  }
}
