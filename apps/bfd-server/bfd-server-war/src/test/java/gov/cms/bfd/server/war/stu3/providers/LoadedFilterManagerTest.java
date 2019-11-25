package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import gov.cms.bfd.model.rif.FilterSerialization;
import gov.cms.bfd.model.rif.LoadedFile;
import gov.cms.bfd.model.rif.RifFileType;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import org.junit.Assert;
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

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void singleFile() throws IOException {
    // Create a few times
    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    Date[] dates = new Date[10];
    for (int i = 0; i < dates.length; i++) {
      dates[i] = Date.from(now.plusSeconds(i));
    }

    // Create sample1
    LoadedFile sample1 = buildLoadedFile(1, dates[2], dates[5]);
    final DateRangeParam during1 = new DateRangeParam(dates[3], dates[4]);
    final DateRangeParam before1 = new DateRangeParam(dates[0], dates[1]);
    final DateRangeParam after1 = new DateRangeParam(dates[6], dates[7]);
    final DateRangeParam afterRefresh = new DateRangeParam(dates[3], dates[9]);
    final DateRangeParam afterUnbounded =
        new DateRangeParam()
            .setLowerBound(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, dates[6]));
    final DateRangeParam beforeUnbounded =
        new DateRangeParam()
            .setUpperBound(new DateParam(ParamPrefixEnum.LESSTHAN_OR_EQUALS, dates[7]));

    final LoadedFilterManager filterManager = new LoadedFilterManager(0);

    // Test before refresh
    Assert.assertFalse(filterManager.isInKnownBounds(during1));
    Assert.assertFalse(filterManager.isResultSetEmpty(SAMPLE_BENE, during1));
    Assert.assertFalse(filterManager.isResultSetEmpty(INVALID_BENE, during1));

    // Refresh with sample 1
    filterManager.refreshFiltersDirectly(Arrays.asList(sample1), dates[8]);
    Assert.assertEquals(1, filterManager.getFilters().size());
    Assert.assertEquals(dates[2], filterManager.getKnownLowerBound());
    Assert.assertEquals(dates[8], filterManager.getKnownUpperBound());

    // Test after refresh
    Assert.assertTrue(filterManager.isInKnownBounds(during1));
    Assert.assertFalse(
        "Result should not be empty", filterManager.isResultSetEmpty(SAMPLE_BENE, during1));
    Assert.assertTrue(
        "Invalid bene should mean empty result set",
        filterManager.isResultSetEmpty(INVALID_BENE, during1));

    // Test before sample time
    Assert.assertFalse(
        "Should be before the filters and always be false",
        filterManager.isResultSetEmpty(SAMPLE_BENE, before1));
    Assert.assertFalse(
        "Should be before the filters and always be false",
        filterManager.isResultSetEmpty(INVALID_BENE, before1));

    // Test after sample time
    Assert.assertTrue(
        "Should be not have any matches", filterManager.isResultSetEmpty(SAMPLE_BENE, after1));
    Assert.assertTrue(
        "Should be not have any matches", filterManager.isResultSetEmpty(INVALID_BENE, after1));

    // Test after sample time
    Assert.assertFalse(
        "Expected false with time period after refresh time",
        filterManager.isResultSetEmpty(SAMPLE_BENE, afterRefresh));
    Assert.assertFalse(
        "Expected false with time period after refresh time",
        filterManager.isResultSetEmpty(INVALID_BENE, afterRefresh));

    // Test unbounded time
    Assert.assertFalse(
        "Expected false with unbounded time",
        filterManager.isResultSetEmpty(INVALID_BENE, afterUnbounded));
    Assert.assertFalse(
        "Expected false with with unbounded time",
        filterManager.isResultSetEmpty(INVALID_BENE, beforeUnbounded));

    // Test exclusive & inclusive time
    final DateRangeParam inclusive1 = new DateRangeParam(dates[5], dates[6]);
    final DateRangeParam exclusive1 =
        new DateRangeParam().setLowerBoundExclusive(dates[5]).setUpperBoundExclusive(dates[6]);
    Assert.assertFalse(
        "Time period should match and result is not empty",
        filterManager.isResultSetEmpty(SAMPLE_BENE, inclusive1));
    Assert.assertTrue(
        "Time period should not match and result is empty",
        filterManager.isResultSetEmpty(SAMPLE_BENE, exclusive1));
  }

  @Test
  public void multipleFile() throws IOException {
    // Create a few times
    final Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    final Date[] dates = new Date[20];
    for (int i = 0; i < dates.length; i++) {
      dates[i] = Date.from(now.plusSeconds(i));
    }

    // Create sample1
    final LoadedFile sample1 = buildLoadedFile(1, dates[2], dates[5]);
    final DateRangeParam during1 = new DateRangeParam(dates[3], dates[4]);

    // Create sample2
    final LoadedFile sample2 = buildLoadedFile(2, dates[8], dates[11]);
    final DateRangeParam during2 = new DateRangeParam(dates[9], dates[10]);
    final DateRangeParam between = new DateRangeParam(dates[6], dates[7]);
    final DateRangeParam both = new DateRangeParam(dates[2], dates[11]);

    final LoadedFilterManager filterManager = new LoadedFilterManager(0);
    filterManager.refreshFiltersDirectly(Arrays.asList(sample1, sample2), dates[16]);
    Assert.assertEquals(2, filterManager.getFilters().size());
    Assert.assertEquals(dates[2], filterManager.getKnownLowerBound());
    Assert.assertEquals(dates[16], filterManager.getKnownUpperBound());
    Assert.assertTrue(
        filterManager
            .getFilters()
            .get(0)
            .getLastUpdated()
            .after(filterManager.getFilters().get(1).getLastUpdated()));

    // Test sample 1
    Assert.assertFalse(
        "Result should not be empty for this valid bene",
        filterManager.isResultSetEmpty(SAMPLE_BENE, during1));
    Assert.assertTrue(
        "Invalid bene should mean empty result set",
        filterManager.isResultSetEmpty(INVALID_BENE, during1));

    // Test sample 2
    Assert.assertFalse(
        "Result should not be empty for this valid bene",
        filterManager.isResultSetEmpty(SAMPLE_BENE, during2));
    Assert.assertTrue(
        "Invalid bene should mean empty result set",
        filterManager.isResultSetEmpty(INVALID_BENE, during2));

    // Test in between samples
    Assert.assertTrue(
        "Expected result set to be empty between loads",
        filterManager.isResultSetEmpty(SAMPLE_BENE, between));
    Assert.assertTrue(
        "Expected result set to be empty between loads",
        filterManager.isResultSetEmpty(INVALID_BENE, between));

    // Test sample 2
    Assert.assertFalse(
        "Result should not be empty for this valid bene",
        filterManager.isResultSetEmpty(SAMPLE_BENE, both));
    Assert.assertTrue(
        "Invalid bene should mean empty result set",
        filterManager.isResultSetEmpty(INVALID_BENE, both));
  }

  @Test
  public void testEmptyFile() throws IOException {
    // Create a few times
    final Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    final Date[] dates = new Date[20];
    for (int i = 0; i < dates.length; i++) {
      dates[i] = Date.from(now.plusSeconds(i));
    }

    final LoadedFilterManager filterManager = new LoadedFilterManager();

    // Create empty file
    final LoadedFile emptyFile1 = buildLoadedFile(1, dates[2], dates[2]);
    final DateRangeParam before1 = new DateRangeParam(dates[0], dates[1]);
    final DateRangeParam during1 = new DateRangeParam(dates[2], dates[3]);

    filterManager.refreshFiltersDirectly(Arrays.asList(emptyFile1), dates[4]);

    // Test empty
    Assert.assertFalse(
        "Result should not be empty for this early period",
        filterManager.isResultSetEmpty(INVALID_BENE, before1));
    Assert.assertTrue(
        "Invalid bene should mean empty result set",
        filterManager.isResultSetEmpty(INVALID_BENE, during1));
  }

  @Test
  public void testUpdateFile() throws IOException {
    // Create a few times
    final Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    final Date[] dates = new Date[20];
    for (int i = 0; i < dates.length; i++) {
      dates[i] = Date.from(now.plusSeconds(i));
    }

    final LoadedFilterManager filterManager = new LoadedFilterManager(0);

    // refresh with an emptyFile
    final LoadedFile emptyFile1 = buildLoadedFile(1, dates[2], dates[2]);
    filterManager.refreshFiltersDirectly(Arrays.asList(emptyFile1), dates[4]);

    // Update the file and refresh again
    final LoadedFile file1 = buildLoadedFile(1, dates[2], dates[5]);
    filterManager.refreshFiltersDirectly(Arrays.asList(file1), dates[8]);

    // Test the new filter
    Assert.assertEquals(1, filterManager.getFilters().size());
    final DateRangeParam during1 = new DateRangeParam(dates[2], dates[3]);
    Assert.assertFalse(
        "Expected valid bene and range to not be empty",
        filterManager.isResultSetEmpty(SAMPLE_BENE, during1));
    Assert.assertTrue(
        "Expected invalid bene to be empty", filterManager.isResultSetEmpty(INVALID_BENE, during1));
    final DateRangeParam after1 = new DateRangeParam(dates[6], dates[7]);
    Assert.assertTrue(
        "Expected valid bene and range to not be empty",
        filterManager.isResultSetEmpty(SAMPLE_BENE, after1));
  }

  @Test
  public void testIncompleteLoadedFiles() throws IOException {
    final LoadedFilterManager filterManager = new LoadedFilterManager(0);
    // Create a few times
    final Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    final Date[] dates = new Date[20];
    for (int i = 0; i < dates.length; i++) {
      dates[i] = Date.from(now.plusSeconds(i));
    }

    final LoadedFile file1 = buildLoadedFile(1, dates[2], dates[5]);
    filterManager.refreshFiltersDirectly(Arrays.asList(file1), dates[8]);
    Assert.assertEquals(1, filterManager.getFilters().size());
    Assert.assertEquals(dates[8], filterManager.getKnownUpperBound());

    final LoadedFile file2Incomplete = buildIncompleteFile(2, dates[9]);
    filterManager.refreshFiltersDirectly(Arrays.asList(file1, file2Incomplete), dates[10]);
    Assert.assertEquals(dates[9], filterManager.getKnownUpperBound());
    Assert.assertEquals(1, filterManager.getFilters().size());

    final LoadedFile file2Complete = buildLoadedFile(2, dates[9], dates[11]);
    filterManager.refreshFiltersDirectly(Arrays.asList(file1, file2Complete), dates[11]);
    Assert.assertEquals(dates[11], filterManager.getKnownUpperBound());
    Assert.assertEquals(2, filterManager.getFilters().size());
  }

  public LoadedFile buildLoadedFile(long loadedFileId, Date firstUpdated, Date lastUpdated)
      throws IOException {
    final String[] benes = {SAMPLE_BENE};
    final byte[] beneBytes = FilterSerialization.serialize(benes);
    return new LoadedFile(
        loadedFileId,
        RifFileType.BENEFICIARY.toString(),
        benes.length,
        FilterSerialization.DEFAULT_SERIALIZATION,
        beneBytes,
        firstUpdated,
        lastUpdated);
  }

  public LoadedFile buildIncompleteFile(long loadedFileId, Date firstUpdated) {
    return new LoadedFile(
        loadedFileId,
        RifFileType.BENEFICIARY.toString(),
        0,
        FilterSerialization.DEFAULT_SERIALIZATION,
        null,
        firstUpdated,
        null);
  }
}
