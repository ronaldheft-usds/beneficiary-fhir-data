package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import com.google.common.hash.BloomFilter;
import java.util.Date;

/**
 * LoadedFile filters are used to determine if a given beneficiary was updated in particular loaded
 * file. Beneath the covers, they use BloomFilters (see <a
 * href="https://en.wikipedia.org/wiki/Bloom_filter">Bloom Filters</a>) which are space efficient.
 */
public class LoadedFileFilter {
  private long loadedFileId;
  private Date firstUpdated;
  private Date lastUpdated;
  private BloomFilter<String> updatedBeneficiaries;

  /**
   * Build a filter for a LoadedFile
   *
   * @param loadedFileId for this filter
   * @param firstUpdated for this filter
   * @param lastUpdated for this filter
   * @param updatedBeneficiaries bloom filter for this filter
   */
  public LoadedFileFilter(
      long loadedFileId,
      Date firstUpdated,
      Date lastUpdated,
      BloomFilter<String> updatedBeneficiaries) {
    this.loadedFileId = loadedFileId;
    this.firstUpdated = firstUpdated;
    this.lastUpdated = lastUpdated;
    this.updatedBeneficiaries = updatedBeneficiaries;
  }

  /**
   * Tests the filter's time span overlaps the passed in date range.
   *
   * @param dateRangeParam to compare
   * @return true if there is some overlap
   */
  public boolean matchesDateRange(DateRangeParam dateRangeParam) {
    if (dateRangeParam == null) return true;

    DateParam upperBound = dateRangeParam.getUpperBound();
    if (upperBound != null) {
      switch (upperBound.getPrefix()) {
        case LESSTHAN:
          if (upperBound.getValue().getTime() <= getFirstUpdated().getTime()) {
            return false;
          }
          break;
        case LESSTHAN_OR_EQUALS:
          if (upperBound.getValue().getTime() < getFirstUpdated().getTime()) {
            return false;
          }
          break;
        default:
          throw new IllegalArgumentException();
      }
    }

    DateParam lowerBound = dateRangeParam.getLowerBound();
    if (lowerBound != null) {
      switch (lowerBound.getPrefix()) {
        case GREATERTHAN:
          if (lowerBound.getValue().getTime() >= getLastUpdated().getTime()) {
            return false;
          }
          break;
        case GREATERTHAN_OR_EQUALS:
          if (lowerBound.getValue().getTime() > getLastUpdated().getTime()) {
            return false;
          }
          break;
        default:
          throw new IllegalArgumentException();
      }
    }

    return true;
  }

  /**
   * Might the filter contain the passed in beneficiary
   *
   * @param beneficiaryId to test
   * @return true if the filter may contain the beneficiary
   */
  public boolean mightContain(String beneficiaryId) {
    return updatedBeneficiaries.mightContain(beneficiaryId);
  }

  /** @return the fileId */
  public long getLoadedFileId() {
    return loadedFileId;
  }

  /** @param loadedFileId the fileId to set */
  public void setLoadedFileId(long loadedFileId) {
    this.loadedFileId = loadedFileId;
  }

  /** @return the firstUpdated */
  public Date getFirstUpdated() {
    return firstUpdated;
  }

  /** @param firstUpdated the firstUpdated to set */
  public void setFirstUpdated(Date firstUpdated) {
    this.firstUpdated = firstUpdated;
  }

  /** @return the lastUpdated */
  public Date getLastUpdated() {
    return lastUpdated;
  }

  /** @param lastUpdated the lastUpdated to set */
  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  /** @return the updatedBeneficiaries */
  public BloomFilter<String> getUpdatedBeneficiaries() {
    return updatedBeneficiaries;
  }

  /** @param updatedBeneficiaries the updatedBeneficiaries to set */
  public void setUpdatedBeneficiaries(BloomFilter<String> updatedBeneficiaries) {
    this.updatedBeneficiaries = updatedBeneficiaries;
  }
}
