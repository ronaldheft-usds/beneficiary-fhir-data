package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
import java.time.Instant;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;

public class QueryUtilsTest {
  @Test
  public void testInRange() {
    Date lowerDate = new Date();
    Date middleDate = Date.from(Instant.now().plusSeconds(500));
    Date upperDate = Date.from(Instant.now().plusSeconds(1000));

    Assert.assertFalse(
        QueryUtils.isInRange(lowerDate, new DateRangeParam().setLowerBoundExclusive(lowerDate)));
    Assert.assertTrue(
        QueryUtils.isInRange(lowerDate, new DateRangeParam().setLowerBoundInclusive(lowerDate)));
    Assert.assertFalse(
        QueryUtils.isInRange(lowerDate, new DateRangeParam().setLowerBoundInclusive(middleDate)));

    Assert.assertFalse(
        QueryUtils.isInRange(upperDate, new DateRangeParam().setUpperBoundExclusive(upperDate)));
    Assert.assertTrue(
        QueryUtils.isInRange(upperDate, new DateRangeParam().setUpperBoundInclusive(upperDate)));
    Assert.assertFalse(
        QueryUtils.isInRange(upperDate, new DateRangeParam().setUpperBoundInclusive(middleDate)));

    Assert.assertTrue(QueryUtils.isInRange(middleDate, new DateRangeParam(lowerDate, upperDate)));
    Assert.assertFalse(QueryUtils.isInRange(lowerDate, new DateRangeParam(middleDate, upperDate)));
    Assert.assertFalse(QueryUtils.isInRange(upperDate, new DateRangeParam(lowerDate, middleDate)));
  }
}
