package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
import com.google.common.hash.BloomFilter;
import gov.cms.bfd.model.rif.LoadedBatch;
import gov.cms.bfd.model.rif.LoadedFile;
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
import javax.persistence.criteria.Join;
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
    this.knownLowerBound = new Date(); // Empty bound
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
        Join<LoadedFile, LoadedBatch> b = f.join("batches");
        partial
            .multiselect(f.get("loadedFileId"), f.get("created"), cb.max(b.get("created")))
            .groupBy(f.get("loadedFileId"), f.get("created"))
            .orderBy(cb.desc(f.get("created")));
        List<Object[]> rows = entityManager.createQuery(partial).getResultList();

        // Fetch a full entity
        Function<Long, List<LoadedBatch>> fetchBatches =
            fileId -> {
              final CriteriaQuery<LoadedBatch> fetch = cb.createQuery(LoadedBatch.class);
              final Root<LoadedBatch> l = fetch.from(LoadedBatch.class);
              fetch.select(l).where(cb.equal(l.get("loadedFileId"), fileId));
              return entityManager.createQuery(fetch).getResultList();
            };

        // Rebuild the filter list
        this.filters = buildFilters(filters, rows, fetchBatches);

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
        this.knownLowerBound = calcLowerBound(rows, this.knownUpperBound);
      }
    } catch (Exception ex) {
      LOGGER.error("Error found refreshing LoadedFile filters", ex);
    }
  }

  /**
   * Set the current state. Used in tests.
   *
   * @param filters to use
   * @param knownLowerBound to use
   * @param knownUpperBound to use
   */
  public void set(List<LoadedFileFilter> filters, Date knownLowerBound, Date knownUpperBound) {
    this.filters = filters;
    this.knownLowerBound = knownLowerBound;
    this.knownUpperBound = knownUpperBound;
  }

  /** @return a info about the filter manager state */
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
   * Dev Note: The following methods encapusulate the logic of the manager. They seperated from the
   * state of the manager to make it easier to test. They should be considered private to the class,
   * but they are made public for testing.
   */

  /**
   * Build a new filter list, reusing the existing list
   *
   * @param existing filters to reuse if possible
   * @param loadedFileRows Tuples of loadedFileId, LoadedFile.created, max(LoadedBatch.created)
   * @param fetch to use retrieve list of LoadedBatch
   * @return a new filter list
   */
  public static List<LoadedFileFilter> buildFilters(
      List<LoadedFileFilter> existing,
      List<Object[]> loadedFileRows,
      Function<Long, List<LoadedBatch>> fetch) {
    return loadedFileRows.stream()
        .filter(row -> row[2] != null) // filter out rows that do not have a batch
        .map(
            row -> {
              final long loadedFileId = (Long) row[0]; // from LoadedFile.loadedFileId
              final Date firstUpdated = (Date) row[1]; // from LoadedFile.created
              final Date lastUpdated = (Date) row[2]; // from max(LoadedBatch.created)
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
                    return buildFilter(loadedFileId, firstUpdated, fetch);
                  });
            })
        .collect(Collectors.toList());
  }

  /**
   * Build a filter for this loaded file. Should be called in a synchronized context.
   *
   * @param fileId to build a filter for
   * @param firstUpdated time stamp
   * @param fetchById a function which returns a list of batches
   * @return a new filter
   */
  public static LoadedFileFilter buildFilter(
      long fileId, Date firstUpdated, Function<Long, List<LoadedBatch>> fetchById) {
    final List<LoadedBatch> loadedBatches = fetchById.apply(fileId);
    if (loadedBatches.size() == 0) {
      throw new IllegalArgumentException("Batches cannot be empty for a filter");
    }
    final int estimatedCount =
        loadedBatches.get(0).getBeneficiaries().size() * loadedBatches.size();
    final BloomFilter<String> bloomFilter = LoadedFileFilter.createFilter(estimatedCount);
    Date lastUpdated = firstUpdated;
    for (LoadedBatch batch : loadedBatches) {
      for (String beneficiary : batch.getBeneficiaries()) {
        bloomFilter.put(beneficiary);
      }
      if (batch.getCreated().after(lastUpdated)) {
        lastUpdated = batch.getCreated();
      }
    }

    LOGGER.info("Built a filter for {} with {} batches", fileId, loadedBatches.size());
    return new LoadedFileFilter(
        fileId, loadedBatches.size(), firstUpdated, lastUpdated, bloomFilter);
  }

  /**
   * Calculate the upper bound based on unfinished loaded files and the passed upper bound
   *
   * @param fileRows Tuples of loadedFileId, LoadedFile.created, max(LoadedBatch.created)
   * @param refreshTime the current time when the fileRows was fetched
   * @return
   */
  public static Date calcUpperBound(List<Object[]> fileRows, Date refreshTime) {
    Optional<Date> maxLastUpdated =
        fileRows.stream()
            .map(r -> r[2] != null ? (Date) r[2] : (Date) r[1])
            .sorted((a, b) -> b.compareTo(a))
            .findFirst();
    return maxLastUpdated.isPresent() && maxLastUpdated.get().after(refreshTime)
        ? maxLastUpdated.get()
        : refreshTime;
  }

  /**
   * Calculate the lower bound based on the loaded file information
   *
   * @param fileRows Tuples of loadedFileId, LoadedFile.created, max(LoadedBatch.created)
   * @param lowerBound is bound to use if no rows are present
   * @return new calculated bound or null for an empty list
   */
  public static Date calcLowerBound(List<Object[]> fileRows, Date lowerBound) {
    Optional<Object[]> min = fileRows.stream().min((a, b) -> ((Date) a[1]).compareTo((Date) b[1]));
    return min.isPresent() ? (Date) min.get()[1] : lowerBound;
  }
}
