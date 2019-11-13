package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link gov.cms.bfd.server.war.stu3.providers.ClusterFilter}. */
public final class ClusterFilterTest {

  @Test
  public void testMatchesDateRange() {
    BloomFilter<String> emptyFilter =
        BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 10);
    ClusterFilter filter1 =
        new ClusterFilter(
            1,
            Date.from(Instant.now().minusSeconds(10)),
            Date.from(Instant.now().minusSeconds(5)),
            emptyFilter);

    Assert.assertTrue(
        "Expected null range to be treated as an infinite range", filter1.matchesDateRange(null));
    Assert.assertTrue(
        "Expected empty range to be treated as an infinite range",
        filter1.matchesDateRange(new DateRangeParam()));

    DateRangeParam sinceYesterday =
        new DateRangeParam(
            new DateParam()
                .setPrefix(ParamPrefixEnum.GREATERTHAN)
                .setValue(Date.from(Instant.now().minus(1, ChronoUnit.DAYS))));
    Assert.assertTrue(
        "Expected since yesterday period to cover", filter1.matchesDateRange(sinceYesterday));

    DateRangeParam beforeNow =
        new DateRangeParam(
            new DateParam()
                .setPrefix(ParamPrefixEnum.LESSTHAN_OR_EQUALS)
                .setValue(Date.from(Instant.now())));
    Assert.assertTrue(
        "Expected since yesterday period to cover", filter1.matchesDateRange(beforeNow));

    DateRangeParam beforeYesterday =
        new DateRangeParam(
            new DateParam()
                .setPrefix(ParamPrefixEnum.LESSTHAN)
                .setValue(Date.from(Instant.now().minus(1, ChronoUnit.DAYS))));
    Assert.assertFalse(
        "Expected before yesterday period to not match", filter1.matchesDateRange(beforeYesterday));

    DateRangeParam afterNow =
        new DateRangeParam(
            new DateParam()
                .setPrefix(ParamPrefixEnum.GREATERTHAN_OR_EQUALS)
                .setValue(Date.from(Instant.now())));
    Assert.assertFalse(
        "Expected after now period to not match", filter1.matchesDateRange(afterNow));

    DateRangeParam beforeSevenSeconds =
        new DateRangeParam(
            new DateParam()
                .setPrefix(ParamPrefixEnum.LESSTHAN)
                .setValue(Date.from(Instant.now().minus(7, ChronoUnit.SECONDS))));
    Assert.assertTrue(
        "Expected partial match to match", filter1.matchesDateRange(beforeSevenSeconds));

    DateRangeParam afterSevenSeconds =
        new DateRangeParam(
            new DateParam()
                .setPrefix(ParamPrefixEnum.GREATERTHAN)
                .setValue(Date.from(Instant.now().minus(7, ChronoUnit.SECONDS))));
    Assert.assertTrue(
        "Expected partial match to match", filter1.matchesDateRange(afterSevenSeconds));

    DateRangeParam sevenSeconds =
        new DateRangeParam(
            Date.from(Instant.now().minusSeconds(8)), Date.from(Instant.now().minusSeconds(7)));
    Assert.assertTrue("Expected partial match to match", filter1.matchesDateRange(sevenSeconds));
  }

  @Test
  public void testMightContain() {
    // Very small test on the Guava implementation of BloomFilters. Assume this package works.
    BloomFilter<String> smallFilter =
        BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 10);
    smallFilter.put("1");
    smallFilter.put("100");
    smallFilter.put("101");

    ClusterFilter filter1 =
        new ClusterFilter(
            1,
            Date.from(Instant.now().minusSeconds(10)),
            Date.from(Instant.now().minusSeconds(5)),
            smallFilter);

    Assert.assertTrue("Expected to contain this", filter1.mightContain("1"));
    Assert.assertFalse("Expected to not contain this", filter1.mightContain("89DS"));
  }
}
