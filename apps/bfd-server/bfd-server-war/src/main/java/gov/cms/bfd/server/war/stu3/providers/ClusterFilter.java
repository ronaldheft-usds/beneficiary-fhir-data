package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
import com.google.common.hash.BloomFilter;
import java.util.Date;

/**
 * Cluster filters are used to determine if a given beneficiary was updated in particular cluster.
 * Beneath the covers, they use BloomFilters (see <a
 * href="https://en.wikipedia.org/wiki/Bloom_filter">Bloom Filters</a>) which are space efficient.
 */
public class ClusterFilter {
  private long clusterId;
  private Date firstUpdated;
  private Date lastUpdated;
  private BloomFilter<String> updatedBeneficiaries;

  /**
   * Build a cluster filter
   *
   * @param clusterId for this filter
   * @param firstUpdated for this filter
   * @param lastUpdated for this filter
   * @param updatedBeneficiaries bloom filter for this filter
   */
  public ClusterFilter(
      long clusterId,
      Date firstUpdated,
      Date lastUpdated,
      BloomFilter<String> updatedBeneficiaries) {
    this.clusterId = clusterId;
    this.firstUpdated = firstUpdated;
    this.lastUpdated = lastUpdated;
    this.updatedBeneficiaries = updatedBeneficiaries;
  }

  /** @return the clusterId */
  public long getClusterId() {
    return clusterId;
  }

  /** @return the firstUpdated */
  public Date getFirstUpdated() {
    return firstUpdated;
  }

  /** @return the lastUpdated */
  public Date getLastUpdated() {
    return lastUpdated;
  }

  /** @return the updatedBeneficiaries */
  public BloomFilter<String> getUpdatedBeneficiaries() {
    return updatedBeneficiaries;
  }

  /**
   * Tests the cluster's time span overlaps the passed in date range.
   *
   * @param dateRangeParam to compare
   * @return true if there is some overlap
   */
  public boolean matchesDateRange(DateRangeParam dateRangeParam) {
    if (dateRangeParam == null) return true;

    Date upperRange = dateRangeParam.getUpperBoundAsInstant();
    if (upperRange != null && upperRange.compareTo(getFirstUpdated()) <= 0) return false;

    Date lowerRange = dateRangeParam.getLowerBoundAsInstant();
    if (lowerRange != null && lowerRange.compareTo(getLastUpdated()) >= 0) return false;

    return true;
  }

  /**
   * Might the cluster contain the passed in beneficiary
   *
   * @param beneficiaryId to test
   * @return true if the cluster may contain the beneficiary
   */
  public boolean mightContain(String beneficiaryId) {
    return updatedBeneficiaries.mightContain(beneficiaryId);
  }
}
