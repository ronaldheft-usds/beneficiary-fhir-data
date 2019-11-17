package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import gov.cms.bfd.model.rif.LoadedFile;
import gov.cms.bfd.model.rif.RifFileType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoadedFilterManagerTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadedFilterManagerIT.class);
  private static final String SAMPLE_BENE = "567834";
  private static final String INVALID_BENE = "1";

  @Test
  public void singleFile() {
    // Create a few times
    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    Date[] dates = new Date[10];
    for (int i = 0; i < dates.length; i++) {
      dates[i] = Date.from(now.plusSeconds(i));
    }

    // Create sample1
    LoadedFile sample1 = new LoadedFile(1, RifFileType.BENEFICIARY.toString(), dates[2], dates[5]);
    DateRangeParam during1 = new DateRangeParam(dates[3], dates[4]);
    DateRangeParam before1 = new DateRangeParam(dates[0], dates[1]);
    DateRangeParam after1 = new DateRangeParam(dates[6], dates[7]);
    DateRangeParam afterRefresh = new DateRangeParam(dates[3], dates[9]);
    DateRangeParam afterUnbounded =
        new DateRangeParam()
            .setLowerBound(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, dates[6]));
    DateRangeParam beforeUnbounded =
        new DateRangeParam()
            .setUpperBound(new DateParam(ParamPrefixEnum.LESSTHAN_OR_EQUALS, dates[7]));

    LoadedFilterManager filterManager = new LoadedFilterManager();

    // Test before refresh
    Assert.assertFalse(
        "Execpted false before refresh", filterManager.isResultSetEmpty(SAMPLE_BENE, during1));
    Assert.assertFalse(
        "Execpted false before refresh", filterManager.isResultSetEmpty(INVALID_BENE, during1));

    // Refresh with sample 1
    filterManager.refreshFiltersDirectly(
        Arrays.asList(sample1), Arrays.asList(SAMPLE_BENE), dates[8]);

    // Test after refresh
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
  }

  @Test
  public void multipleFile() {
    // Create a few times
    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    Date[] dates = new Date[20];
    for (int i = 0; i < dates.length; i++) {
      dates[i] = Date.from(now.plusSeconds(i));
    }

    // Create sample1
    LoadedFile sample1 = new LoadedFile(1, RifFileType.BENEFICIARY.toString(), dates[2], dates[5]);
    DateRangeParam during1 = new DateRangeParam(dates[3], dates[4]);

    // Create sample2
    LoadedFile sample2 = new LoadedFile(2, RifFileType.BENEFICIARY.toString(), dates[8], dates[11]);
    DateRangeParam during2 = new DateRangeParam(dates[9], dates[10]);
    DateRangeParam between = new DateRangeParam(dates[6], dates[7]);
    DateRangeParam both = new DateRangeParam(dates[2], dates[11]);

    LoadedFilterManager filterManager = new LoadedFilterManager();
    filterManager.refreshFiltersDirectly(
        Arrays.asList(sample1, sample2), Arrays.asList(SAMPLE_BENE), dates[16]);

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
}
