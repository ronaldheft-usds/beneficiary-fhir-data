package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
import com.google.common.hash.BloomFilter;
import com.justdavis.karl.misc.exceptions.BadCodeMonkeyException;
import gov.cms.bfd.model.rif.FilterSerialization;
import gov.cms.bfd.model.rif.LoadedFile;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monitors the loaded files in the database and creates Bloom filters to match these files.
 * Thread-safe.
 */
@Component
public class LoadedFilterManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadedFilterManager.class);

  private List<LoadedFileFilter> filters = Arrays.asList();
  private EntityManager entityManager;
  private Date refreshTime;

  /** @return the list of current filters. Newest first. */
  List<LoadedFileFilter> getFilters() {
    return filters;
  }

  /**
   * Setup the JPA entityManager for the database to query
   *
   * @param entityManager
   */
  @PersistenceContext
  void setEntityManager(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * Is the result set going to be empty for this beneficiary and time period?
   *
   * @param beneficiaryId to test
   * @param lastUpdatedRange to test
   * @return true if the results set is empty. false if the result set *may* contain items.
   */
  public boolean isResultSetEmpty(String beneficiaryId, DateRangeParam lastUpdatedRange) {
    synchronized (this) {
      if (lastUpdatedRange == null || refreshTime == null || getFilters().size() == 0) return false;

      // The manager doesn't know the state of DB after the refreshTime. So, we can
      // immediately reject ranges after the refreshTime. As well as the intervals before the oldest
      // filter.
      final Date upperBound = lastUpdatedRange.getUpperBoundAsInstant();
      if (upperBound == null || upperBound.getTime() > getRefreshTime().getTime()) return false;
      final LoadedFileFilter oldestFilter = getFilters().get(getFilters().size() - 1);
      final Date lowerBound = lastUpdatedRange.getLowerBoundAsInstant();
      if (lowerBound == null || lowerBound.getTime() < oldestFilter.getFirstUpdated().getTime())
        return false;

      // Within a interval that have filters for
      final List<LoadedFileFilter> filters = getFilters();
      for (LoadedFileFilter filter : filters) {
        if (filter.matchesDateRange(lastUpdatedRange)) {
          if (filter.mightContain(beneficiaryId)) {
            return false;
          }
        } else if (filter.getLastUpdated().getTime() < lowerBound.getTime()) {
          // filters are sorted in descending by lastUpdated time, so we can exit early from this
          // loop
          return true;
        }
      }
      return true;
    }
  }

  /** Called periodically to build and refresh the filters list. */
  @Scheduled(fixedDelay = 10000, initialDelay = 10000)
  public void refreshFilters() {
    refreshFiltersWithDelay(5);
  }

  /**
   * Refresh with some amount of propgation delay. Useful for testing.
   *
   * @param delaySeconds to subtract from the refreshTime
   */
  public void refreshFiltersWithDelay(long delaySeconds) {
    /**
     * Dev note: the pipeline has a process to trim the files list. Nevertheless, building a set of
     * bloom filters may take a while. This method is expected to be called on it's own thread, so
     * this filter building process can happen without interferring with serving. Also, the refresh
     * process will be quick when no loaded file changes are found. Care is taken to ensure that
     * pipeline can write safely while this refresh is happening.
     */

    // Make sure we go back to the DB, not the cache, because of the pipeline may write to DB
    entityManager.clear();

    try {
      // Build up a query that only includes what we need and doesn't include the filterBytes which
      // can be large
      CriteriaBuilder cb = entityManager.getCriteriaBuilder();
      CriteriaQuery<Object[]> criteria = cb.createQuery(Object[].class);
      Root<LoadedFile> f = criteria.from(LoadedFile.class);
      criteria
          .multiselect(f.get("loadedFileId"), f.get("firstUpdated"), f.get("lastUpdated"))
          .orderBy(cb.desc(f.get("lastUpdated")));
      List<Object[]> rows = entityManager.createQuery(criteria).getResultList();
      /**
       * Dev note: refreshTime should be calculated on the pipeline's clock as other dates, since
       * there is the possiblity of clock skew and db replication delay between the pipeline and the
       * data server. However, this code is running on the data server, so we have to estimate the
       * refesh time. To do add subract a few seconds a safety margin for the possibility of these
       * effects. There methods to do a better estimate, but they are not worth the effort because
       * getting the delay estimate wrong will never affect the correctness of the BFD's results.
       */
      refreshTime = Date.from(Instant.now().minusSeconds(delaySeconds));

      // Update the filters
      for (Object[] row : rows) {
        synchronized (this) {
          long loadedFileId = (Long) row[0];
          Date firstUpdated = (Date) row[1];
          Date lastUpdated = (Date) row[2];
          if (!hasFilterFor(loadedFileId, firstUpdated, lastUpdated)) {
            // This may take few seconds for large 1M > filters
            LoadedFileFilter newFilter = buildFilter(loadedFileId);
            updateFilters(newFilter);
          }
          if (refreshTime.before(lastUpdated)) {
            refreshTime = lastUpdated;
          }
        }
      }
    } catch (Exception ex) {
      LOGGER.error("Error found refreshing LoadedFile filters", ex);
    }
  }

  /**
   * Refresh filters directly from passed in information. Useful for testing.
   *
   * @param loadedFiles to use
   * @param beneficiaries to use, same for all loaded files
   * @param refreshTime to set
   */
  public void refreshFiltersDirectly(
      List<LoadedFile> loadedFiles, List<String> beneficiaries, Date refreshTime) {
    synchronized (this) {
      this.refreshTime = refreshTime;
      loadedFiles.sort(
          (a, b) -> {
            return b.getLastUpdated().compareTo(a.getLastUpdated());
          });

      // Update the filters
      for (LoadedFile loadedFile : loadedFiles) {
        if (!hasFilterFor(
            loadedFile.getLoadedFileId(),
            loadedFile.getFirstUpdated(),
            loadedFile.getLastUpdated())) {
          final LoadedFileFilter newFilter =
              buildFilterDirectly(loadedFile, FilterSerialization.fromList(beneficiaries));
          updateFilters(newFilter);
        }
      }
    }
  }

  /** @return the refreshTime */
  public Date getRefreshTime() {
    return refreshTime;
  }

  /** @param refreshTime the refreshTime to set */
  public void setRefreshTime(Date refreshTime) {
    this.refreshTime = refreshTime;
  }

  /**
   * Does the current list of filters have an associated filter for this loaded file? Should be
   * called in a synchronized context.
   *
   * @param loaded file to find a filter for
   * @return true iff there is a filter associated with this loaded file
   */
  private boolean hasFilterFor(long fileId, Date firstUpdated, Date lastUpdated) {
    return filters.stream()
        .anyMatch(
            filter -> {
              return filter.getLoadedFileId() == fileId
                  && filter.getFirstUpdated().equals(firstUpdated)
                  && filter.getLastUpdated().equals(lastUpdated);
            });
  }

  /**
   * Build a filter for this loaded file. Execution time is O(n) of the number of beneficiaries
   * associated with this loaded file. Should be called in a synchronized context.
   *
   * @param fileId to fetch
   * @return a new filter
   */
  private LoadedFileFilter buildFilter(long fileId) throws IOException, ClassNotFoundException {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<LoadedFile> criteria = cb.createQuery(LoadedFile.class);
    Root<LoadedFile> f = criteria.from(LoadedFile.class);
    criteria.select(f).where(cb.equal(f.get("loadedFileId"), fileId));
    final LoadedFile loadedFile = entityManager.createQuery(criteria).getSingleResult();

    final byte[] filterBytes = loadedFile.getFilterBytes();
    final String filterType = loadedFile.getFilterType();
    final String[] beneficiaries = FilterSerialization.deserialize(filterType, filterBytes);
    if (beneficiaries.length != loadedFile.getCount()) {
      throw new BadCodeMonkeyException(
          "Inconsistent size of benficiaries list in a LoadedFile: "
              + loadedFile.getLoadedFileId());
    }

    final BloomFilter<String> bloomFilter = LoadedFileFilter.createFilter(beneficiaries.length);
    for (String beneficiary : beneficiaries) {
      bloomFilter.put(beneficiary);
    }

    LOGGER.info("Built a filter for {} with {} elements", fileId, loadedFile.getCount());
    return new LoadedFileFilter(
        loadedFile.getLoadedFileId(),
        loadedFile.getFirstUpdated(),
        loadedFile.getLastUpdated(),
        bloomFilter);
  }

  /**
   * Build a filter with provided data. Should be called in a synchronized context.
   *
   * @param loadedFile the loaded file to build a filter
   * @param beneficiaries to fill the filter
   * @return a new filter
   */
  private LoadedFileFilter buildFilterDirectly(LoadedFile loadedFile, String[] beneficiaries) {
    final BloomFilter<String> bloomFilter = LoadedFileFilter.createFilter(beneficiaries.length);
    for (String beneId : beneficiaries) {
      bloomFilter.put(beneId);
    }
    return new LoadedFileFilter(
        loadedFile.getLoadedFileId(),
        loadedFile.getFirstUpdated(),
        loadedFile.getLastUpdated(),
        bloomFilter);
  }

  /**
   * Update the filter list with the new filter. If a filter associated with the same loaded file
   * exsits in the the filter list, replace it. Otherwise, add the new filter. Should be called in a
   * synchronized context.
   *
   * @param newFilter to put into the filter list
   */
  private void updateFilters(LoadedFileFilter newFilter) {
    ArrayList<LoadedFileFilter> copy = new ArrayList<>(filters);
    copy.removeIf(filter -> filter.getLoadedFileId() == newFilter.getLoadedFileId());
    copy.add(newFilter);
    copy.sort((a, b) -> b.getLastUpdated().compareTo(a.getLastUpdated())); // Decsending order
    filters = copy;
  }
}
