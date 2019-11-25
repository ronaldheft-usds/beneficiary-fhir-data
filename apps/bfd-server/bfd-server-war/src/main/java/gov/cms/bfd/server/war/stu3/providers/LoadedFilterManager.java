package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
import com.google.common.hash.BloomFilter;
import com.justdavis.karl.misc.exceptions.BadCodeMonkeyException;
import gov.cms.bfd.model.rif.FilterSerialization;
import gov.cms.bfd.model.rif.LoadedFile;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  // The connection to the DB
  private EntityManager entityManager;

  // The filter set
  private List<LoadedFileFilter> filters;

  // Estimate of the time it takes for a write to the DB to replicate to have a read.
  private int replicaDelay;

  // The limit of the time interval that the this filter set can know about
  private Date knownUpperBound;
  private Date knownLowerBound;

  /** Create a manager for {@link LoadedFileFilter}s. */
  public LoadedFilterManager() {
    this.replicaDelay = 5; // Default estimate
    this.knownLowerBound = new Date();
    this.knownUpperBound = this.knownLowerBound;
    this.filters = Arrays.asList();
  }

  /**
   * Create a manager for {@link LoadedFileFilter}s.
   *
   * @param replicaDelay is an estimate of the time it takes for pipeline write to propogate.
   */
  public LoadedFilterManager(int replicaDelay) {
    this();
    this.replicaDelay = replicaDelay;
  }

  /** @return the list of current filters. Newest first. */
  public List<LoadedFileFilter> getFilters() {
    return filters;
  }

  /**
   * An estimate of the delay between master and replica DB.
   *
   * @return replication delay estimate
   */
  public int getReplicaDelay() {
    return replicaDelay;
  }

  /**
   * The upper bound of the interval that the manager has information about
   *
   * @return the upper bound of the known interval
   */
  public Date getKnownUpperBound() {
    return knownUpperBound;
  }

  /**
   * The lower bound of the interval that the manager has information about
   *
   * @return the lower bound of the known interval
   */
  public Date getKnownLowerBound() {
    return knownLowerBound;
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
      if (beneficiaryId == null || beneficiaryId.isEmpty()) throw new IllegalArgumentException();
      if (!isInKnownBounds(lastUpdatedRange)) {
        // Out of bounds has to be treated as unknown result
        return false;
      }

      // Within the known interval that search for matching filters
      final List<LoadedFileFilter> filters = getFilters();
      for (LoadedFileFilter filter : filters) {
        if (filter.matchesDateRange(lastUpdatedRange)) {
          if (filter.mightContain(beneficiaryId)) {
            return false;
          }
        } else if (filter.getLastUpdated().getTime()
            < lastUpdatedRange.getLowerBoundAsInstant().getTime()) {
          // filters are sorted in descending by lastUpdated time, so we can exit early from this
          // loop
          return true;
        }
      }
      return true;
    }
  }

  /**
   * Test the passed in range against the upper and lower bounds of the filter set.
   *
   * @param range to test against
   * @return true iff the range is within the bounds of the filters
   */
  public boolean isInKnownBounds(DateRangeParam range) {
    synchronized (this) {
      if (range == null || knownUpperBound == null || getFilters().size() == 0) return false;

      // The manager has "known" interval which it has information about.
      final Date upperBound = range.getUpperBoundAsInstant();
      if (upperBound == null || upperBound.getTime() > knownUpperBound.getTime()) return false;
      final Date lowerBound = range.getLowerBoundAsInstant();
      if (lowerBound == null || lowerBound.getTime() < knownLowerBound.getTime()) return false;

      return true;
    }
  }

  /** Called periodically to build and refresh the filters list from the entityManager. */
  @Scheduled(fixedDelay = 10000, initialDelay = 2000)
  public void refreshFilters() {
    /**
     * Dev note: the pipeline has a process to trim the files list. Nevertheless, building a set of
     * bloom filters may take a while. This method is expected to be called on it's own thread, so
     * this filter building process can happen without interfering with serving. Also, the refresh
     * process will be quick when no loaded file changes are found. Care is taken to ensure that
     * pipeline can write safely while this refresh is happening.
     */

    // Make sure we go back to the DB, not the cache, because of the pipeline may write to DB
    entityManager.clear();

    try {
      synchronized (this) {
        // Build up a query that only includes what we need and doesn't include the filterBytes
        // which can be large
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Object[]> partial = cb.createQuery(Object[].class);
        final Root<LoadedFile> f = partial.from(LoadedFile.class);
        partial
            .multiselect(f.get("loadedFileId"), f.get("firstUpdated"), f.get("lastUpdated"))
            .orderBy(cb.desc(f.get("lastUpdated")));
        List<Object[]> rows = entityManager.createQuery(partial).getResultList();

        // Fetch a full entity
        Function<Long, LoadedFile> fetchFullLoadedFile =
            fileId -> {
              final CriteriaQuery<LoadedFile> full = cb.createQuery(LoadedFile.class);
              final Root<LoadedFile> l = full.from(LoadedFile.class);
              full.select(l).where(cb.equal(l.get("loadedFileId"), fileId));
              return entityManager.createQuery(full).getSingleResult();
            };
        this.filters = buildFilters(filters, rows, fetchFullLoadedFile);

        /**
         * Dev note: knownUpperBound should be calculated on the pipeline's clock as other dates,
         * since there is the possibility of clock skew and db replication delay between the
         * pipeline and the data server. However, this code is running on the data server, so we
         * have to estimate the refresh time. To do add subtract a few seconds a safety margin for
         * the possibility of these effects. There methods to do a better estimate, but they are not
         * worth the effort because getting the delay estimate wrong will never affect the
         * correctness of the BFD's results. The replicaDelay estimate can be set to 0 for testing.
         */
        this.knownUpperBound =
            calcUpperBound(rows, Date.from(Instant.now().minusSeconds(replicaDelay)));
        this.knownLowerBound = calcLowerBound(rows);
      }
    } catch (Exception ex) {
      LOGGER.error("Error found refreshing LoadedFile filters", ex);
    }
  }

  /**
   * Refresh filters directly from passed in information. Useful for testing.
   *
   * @param loadedFiles to use
   * @param knownUpperBound to set if the there are no open LoadedFile entries
   */
  public void refreshFiltersDirectly(List<LoadedFile> loadedFiles, Date knownUpperBound) {
    synchronized (this) {
      List<Object[]> rows =
          loadedFiles.stream()
              .sorted(
                  (a, b) -> {
                    final Date aLastUpdated = a.getLastUpdated();
                    final Date bLastUpdated = b.getLastUpdated();
                    if (aLastUpdated == null && bLastUpdated == null) return 0;
                    if (aLastUpdated == null) return -1;
                    if (bLastUpdated == null) return 1;
                    return bLastUpdated.compareTo(aLastUpdated);
                  })
              .map(
                  f -> {
                    Object[] row = {f.getLoadedFileId(), f.getFirstUpdated(), f.getLastUpdated()};
                    return row;
                  })
              .collect(Collectors.toList());
      Function<Long, LoadedFile> fetcher =
          fileId -> {
            return loadedFiles.stream()
                .filter(l -> l.getLoadedFileId() == fileId)
                .findFirst()
                .get();
          };
      this.filters = buildFilters(filters, rows, fetcher);
      this.knownUpperBound = calcUpperBound(rows, knownUpperBound);
      this.knownLowerBound = calcLowerBound(rows);
    }
  }

  /**
   * This pure function encapsulates the logic of refreshing the filter list. Useful for testing the
   * logic but not using directly.
   *
   * @param existing filters to reuse if possible
   * @param loadedFileRows partial tuples from the current LoadedFile table (fileId, firstUpdated,
   *     lastUpdated sorted descending)
   * @param fetch to use retrieve a specific full LoadedFile when needed
   * @return a new filter list
   */
  public static List<LoadedFileFilter> buildFilters(
      List<LoadedFileFilter> existing,
      List<Object[]> loadedFileRows,
      Function<Long, LoadedFile> fetch) {
    return loadedFileRows.stream()
        .filter(row -> row[2] != null)
        .map(
            row -> {
              final long loadedFileId = (Long) row[0];
              final Date firstUpdated = (Date) row[1];
              final Date lastUpdated = (Date) row[2];
              final Optional<LoadedFileFilter> matchingFilter =
                  existing.stream()
                      .filter(
                          filter -> {
                            return filter.getLoadedFileId() == loadedFileId
                                && filter.getFirstUpdated().equals(firstUpdated)
                                && filter.getLastUpdated().equals(lastUpdated);
                          })
                      .findFirst();
              return matchingFilter.orElseGet(
                  () -> {
                    return buildFilter(loadedFileId, fetch);
                  });
            })
        .collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return "LoadedFilterManager [filters.size="
        + (filters != null ? String.valueOf(filters.size()) : "null")
        + ", knownLowerBound="
        + knownLowerBound
        + ", knownUpperBound="
        + knownUpperBound
        + ", replicaDelay="
        + replicaDelay
        + "]";
  }

  /**
   * Build a filter for this loaded file. Execution time is O(n) of the number of beneficiaries
   * associated with this loaded file. Should be called in a synchronized context.
   *
   * @param fileId to fetch
   * @return a new filter
   */
  private static LoadedFileFilter buildFilter(long fileId, Function<Long, LoadedFile> fetch) {
    final LoadedFile loadedFile = fetch.apply(fileId);
    final byte[] filterBytes = loadedFile.getFilterBytes();
    final String filterType = loadedFile.getFilterType();
    String[] beneficiaries = {};
    try {
      beneficiaries = FilterSerialization.deserialize(filterType, filterBytes);
    } catch (IOException ex) {
      throw new BadCodeMonkeyException("Deserialization error", ex);
    } catch (ClassNotFoundException ex) {
      throw new BadCodeMonkeyException("Deserialization error", ex);
    }
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
   * Calculate the upper bound based on unfinished loaded files and the passed upper bound
   *
   * @param rows with (fileId, firstUpdate, lastUpdated) tuple
   * @param upperBound to use if no files are open
   * @return
   */
  private static Date calcUpperBound(List<Object[]> rows, Date upperBound) {
    Optional<Object[]> minOpen =
        rows.stream().filter(r -> r[2] == null).min((a, b) -> ((Date) a[1]).compareTo((Date) b[1]));
    return minOpen.isPresent() ? (Date) minOpen.get()[1] : upperBound;
  }

  /**
   * Calculate the lower bound based on the loaded file information
   *
   * @param rows with (fileId, firstUpdate, lastUpdated) tuple
   * @return new calculate row
   */
  private static Date calcLowerBound(List<Object[]> rows) {
    Optional<Object[]> min = rows.stream().min((a, b) -> ((Date) a[1]).compareTo((Date) b[1]));
    return min.isPresent() ? (Date) min.get()[1] : Date.from(Instant.MAX);
  }
}
