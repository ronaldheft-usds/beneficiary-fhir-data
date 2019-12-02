package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
import gov.cms.bfd.model.rif.LoadedBatch;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoadedFilterManagerTest {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadedFilterManagerIT.class);

  private static final String SAMPLE_BENE = "567834";
  private static final String INVALID_BENE = "1";
  private static final LoadedBatch[] preBatches = new LoadedBatch[8];
  private static final Date[] preDates = new Date[preBatches.length * 5];

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @BeforeClass
  public static void beforeAll() {
    // Create a few timestamps to play with
    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    for (int i = 0; i < preDates.length; i++) {
      preDates[i] = Date.from(now.plusSeconds(i));
    }
    List<String> beneficiaries = Arrays.asList(SAMPLE_BENE);
    for (int i = 0; i < preBatches.length; i++) {
      preBatches[i] = new LoadedBatch(i + 1, (i / 2) + 1, beneficiaries, preDates[i * 5 + 4]);
    }
  }

  @Test
  public void buildEmptyFilter() {
    final MockDb batches = new MockDb().insert(1, preDates[2]);
    final List<Object[]> fileRows = batches.fetchAllRows();
    final List<LoadedFileFilter> loadedFilter =
        LoadedFilterManager.buildFilters(Arrays.asList(), fileRows, batches::fetchById);
    Assert.assertEquals(0, loadedFilter.size());
  }

  @Test
  public void buildOneFilter() {
    final MockDb batches = new MockDb().insert(1, preDates[0]).insert(preBatches[0]);
    final List<Object[]> fileRows = batches.fetchAllRows();
    final List<LoadedFileFilter> filters =
        LoadedFilterManager.buildFilters(Arrays.asList(), fileRows, batches::fetchById);
    Assert.assertEquals(1, filters.size());

    // Test the filter
    final DateRangeParam during1 = new DateRangeParam(preDates[1], preDates[2]);
    Assert.assertTrue(filters.get(0).matchesDateRange(during1));
    Assert.assertEquals(1, filters.get(0).getBatchesCount());
    Assert.assertTrue(filters.get(0).mightContain(SAMPLE_BENE));
    Assert.assertFalse(filters.get(0).mightContain(INVALID_BENE));
  }

  @Test
  public void buildManyFilter() {
    final MockDb batches =
        new MockDb()
            .insert(1, preDates[1])
            .insert(2, preDates[11])
            .insert(3, preDates[21])
            .insert(preBatches[0], preBatches[2], preBatches[4]);
    final List<Object[]> rows = batches.fetchAllRows();
    final List<LoadedFileFilter> filters =
        LoadedFilterManager.buildFilters(Arrays.asList(), rows, batches::fetchById);
    Assert.assertEquals(3, filters.size());
    Assert.assertEquals(1, filters.get(2).getBatchesCount());
  }

  @Test
  public void calcBoundsWithInitial() {
    final List<Object[]> rows = Arrays.asList();
    final Date upper = LoadedFilterManager.calcUpperBound(rows, preDates[7]);
    final Date lower = LoadedFilterManager.calcLowerBound(rows, upper);

    Assert.assertEquals(preDates[7], upper);
    Assert.assertEquals(preDates[7], lower);
  }

  @Test
  public void calcBoundsWithEmpty() {
    final MockDb batches = new MockDb().insert(1, preDates[1]);
    final List<Object[]> fileRows = batches.fetchAllRows();
    final Date upper = LoadedFilterManager.calcUpperBound(fileRows, preDates[7]);
    final Date lower = LoadedFilterManager.calcLowerBound(fileRows, upper);

    Assert.assertEquals(preDates[7], upper);
    Assert.assertEquals(preDates[1], lower);
  }

  @Test
  public void calcBoundsWithOne() {
    final MockDb batches = new MockDb().insert(1, preDates[1]).insert(preBatches[0]);
    final List<Object[]> fileRows = batches.fetchAllRows();
    final Date upper = LoadedFilterManager.calcUpperBound(fileRows, preDates[7]);
    final Date lower = LoadedFilterManager.calcLowerBound(fileRows, upper);

    Assert.assertEquals(preDates[7], upper);
    Assert.assertEquals(preDates[1], lower);
  }

  @Test
  public void calcBoundsWithMany() {
    final MockDb batches =
        new MockDb()
            .insert(1, preDates[1])
            .insert(2, preDates[11])
            .insert(3, preDates[21])
            .insert(preBatches[0], preBatches[2], preBatches[4]);
    final List<Object[]> fileRows = batches.fetchAllRows();
    final Date upper = LoadedFilterManager.calcUpperBound(fileRows, preDates[28]);
    final Date lower = LoadedFilterManager.calcLowerBound(fileRows, upper);

    Assert.assertEquals(preDates[28], upper);
    Assert.assertEquals(preDates[1], lower);
  }

  @Test
  public void testIsResultSetEmpty() {
    final MockDb batches =
        new MockDb()
            .insert(1, preDates[1])
            .insert(2, preDates[11])
            .insert(preBatches[0], preBatches[1], preBatches[2]);
    final List<Object[]> aRows = batches.fetchAllRows();
    final List<LoadedFileFilter> aFilters =
        LoadedFilterManager.buildFilters(Arrays.asList(), aRows, batches::fetchById);
    Assert.assertEquals(2, aFilters.size());
    final Date upperA = LoadedFilterManager.calcUpperBound(aRows, preDates[19]);
    final Date lowerA = LoadedFilterManager.calcLowerBound(aRows, upperA);
    Assert.assertEquals(preDates[19], upperA);
    Assert.assertEquals(preDates[1], lowerA);

    // Setup the manager and test a few lastUpdated ranges
    final LoadedFilterManager filterManagerA = new LoadedFilterManager(0);
    filterManagerA.set(aFilters, lowerA, upperA);
    final DateRangeParam beforeRange = new DateRangeParam(preDates[0], preDates[1]);
    Assert.assertFalse(filterManagerA.isInKnownBounds(beforeRange));
    Assert.assertFalse(filterManagerA.isResultSetEmpty(SAMPLE_BENE, beforeRange));
    final DateRangeParam duringRange1 = new DateRangeParam(preDates[2], preDates[3]);
    Assert.assertTrue(filterManagerA.isInKnownBounds(duringRange1));
    Assert.assertFalse(filterManagerA.isResultSetEmpty(SAMPLE_BENE, duringRange1));
    Assert.assertTrue(filterManagerA.isResultSetEmpty(INVALID_BENE, duringRange1));
    final DateRangeParam duringRange2 =
        new DateRangeParam()
            .setLowerBoundExclusive(preDates[9])
            .setUpperBoundExclusive(preDates[10]);
    Assert.assertTrue(filterManagerA.isInKnownBounds(duringRange2));
    Assert.assertTrue(filterManagerA.isResultSetEmpty(SAMPLE_BENE, duringRange2));
    Assert.assertTrue(filterManagerA.isResultSetEmpty(INVALID_BENE, duringRange2));
    final DateRangeParam afterRange = new DateRangeParam(preDates[20], preDates[21]);
    Assert.assertFalse(filterManagerA.isInKnownBounds(afterRange));
    Assert.assertFalse(filterManagerA.isResultSetEmpty(SAMPLE_BENE, afterRange));
  }

  @Test
  public void testTypicalSequence() {
    final MockDb batches =
        new MockDb()
            .insert(1, preDates[1])
            .insert(2, preDates[11])
            .insert(preBatches[0], preBatches[1], preBatches[2]);
    final List<Object[]> aRows = batches.fetchAllRows();
    final List<LoadedFileFilter> aFilters =
        LoadedFilterManager.buildFilters(Arrays.asList(), aRows, batches::fetchById);
    Assert.assertEquals(2, aFilters.size());
    final Date upperA = LoadedFilterManager.calcUpperBound(aRows, preDates[19]);
    final Date lowerA = LoadedFilterManager.calcLowerBound(aRows, upperA);
    Assert.assertEquals(preDates[19], upperA);
    Assert.assertEquals(preDates[1], lowerA);

    // Simulate starting a new file with no batches
    batches.insert(3, preDates[21]);
    final List<Object[]> bRows = batches.fetchAllRows();
    final List<LoadedFileFilter> bFilters =
        LoadedFilterManager.buildFilters(aFilters, bRows, batches::fetchById);
    final Date upperB = LoadedFilterManager.calcUpperBound(bRows, preDates[22]);
    final Date lowerB = LoadedFilterManager.calcLowerBound(bRows, upperB);
    Assert.assertEquals(preDates[22], upperB);
    Assert.assertEquals(preDates[1], lowerB);

    // Simulate adding a new batch with the same fileId
    batches.insert(preBatches[4]);
    final List<Object[]> cRows = batches.fetchAllRows();
    final List<LoadedFileFilter> cFilters =
        LoadedFilterManager.buildFilters(bFilters, cRows, batches::fetchById);
    final Date upperC = LoadedFilterManager.calcUpperBound(cRows, preDates[25]);
    final Date lowerC = LoadedFilterManager.calcLowerBound(cRows, upperC);
    Assert.assertEquals(3, cFilters.size());
    Assert.assertEquals(1, cFilters.get(1).getBatchesCount());
    Assert.assertEquals(preDates[25], upperC);
    Assert.assertEquals(preDates[1], lowerC);
  }

  @Test
  public void testErrorSequence() {
    final MockDb batches =
        new MockDb()
            .insert(1, preDates[1])
            .insert(2, preDates[11])
            .insert(preBatches[0], preBatches[1], preBatches[2]);
    final List<Object[]> aRows = batches.fetchAllRows();
    final List<LoadedFileFilter> aFilters =
        LoadedFilterManager.buildFilters(Arrays.asList(), aRows, batches::fetchById);
    Assert.assertEquals(2, aFilters.size());

    // Simulate starting a new file with no batches. Don't complete this batch
    batches.insert(3, preDates[21]);
    final List<Object[]> bRows = batches.fetchAllRows();
    final List<LoadedFileFilter> bFilters =
        LoadedFilterManager.buildFilters(aFilters, bRows, batches::fetchById);
    final Date upperB = LoadedFilterManager.calcUpperBound(bRows, preDates[22]);
    final Date lowerB = LoadedFilterManager.calcLowerBound(bRows, upperB);
    Assert.assertEquals(preDates[22], upperB);
    Assert.assertEquals(preDates[1], lowerB);

    // Simulate adding a new batch not in the same file id
    batches.insert(4, preDates[28]).insert(preBatches[6]);
    final List<Object[]> cRows = batches.fetchAllRows();
    final List<LoadedFileFilter> cFilters =
        LoadedFilterManager.buildFilters(bFilters, cRows, batches::fetchById);
    final Date upperC = LoadedFilterManager.calcUpperBound(cRows, preDates[33]);
    final Date lowerC = LoadedFilterManager.calcLowerBound(cRows, upperC);
    Assert.assertEquals(3, cFilters.size());
    Assert.assertEquals(1, cFilters.get(0).getBatchesCount());
    Assert.assertEquals(preDates[34], upperC);
    Assert.assertEquals(preDates[1], lowerC);
  }

  /** Helper class that mocks a DB for a LoadedFilterManager */
  private class MockDb {
    private final ArrayList<LoadedBatch> batches = new ArrayList<>();
    private final ArrayList<Object[]> fileRows = new ArrayList<>();

    MockDb insert(LoadedBatch... batches) {
      Collections.addAll(this.batches, batches);
      return this;
    }

    MockDb insert(long loadedFileId, Date firstUpdated) {
      fileRows.add(new Object[] {loadedFileId, firstUpdated, null});
      return this;
    }

    List<LoadedBatch> fetchById(Long loadedFiledId) {
      return batches.stream()
          .filter(b -> b.getLoadedFileId() == loadedFiledId)
          .collect(Collectors.toList());
    }

    ArrayList<Object[]> fetchAllRows() {
      if (batches.size() + fileRows.size() == 0) {
        return new ArrayList<>();
      }
      ArrayList<Object[]> rows = new ArrayList<>();
      fileRows.forEach(
          row -> {
            long id = (long) row[0];
            Date firstUpdated = (Date) row[1];
            Optional<Date> lastUpdated =
                batches.stream()
                    .filter(b -> b.getLoadedFileId() == id)
                    .map(b -> b.getCreated())
                    .reduce((a, b) -> a.after(b) ? a : b);
            rows.add(
                new Object[] {
                  id, firstUpdated, lastUpdated.isPresent() ? lastUpdated.get() : null
                });
          });
      rows.sort((a, b) -> ((Date) b[1]).compareTo((Date) a[1]));
      return rows;
    }
  }
}
