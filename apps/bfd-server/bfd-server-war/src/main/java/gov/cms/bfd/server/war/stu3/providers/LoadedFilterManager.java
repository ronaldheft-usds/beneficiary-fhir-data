package gov.cms.bfd.server.war.stu3.providers;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import gov.cms.bfd.model.rif.LoadedFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.hibernate.CacheMode;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Monitors the loaded files in the database and creates Bloom filters to match these files. */
@Component
public class LoadedFilterManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadedFilterManager.class);
  // The batch size of beneficiaries to fetch from the LoadedBenficiaries table
  private static final int FETCH_SIZE = 1000;

  private List<LoadedFileFilter> filters = Arrays.asList();
  private EntityManager entityManager;

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
    if (lastUpdatedRange == null || refreshTime == null || getFilters().size() == 0) return false;

    // The manager doesn't know the state of DB after the refreshTime. So, we can
    // immediately reject ranges after the refreshTime. As well as the intervals before the oldest
    // filter.
    Date upperBound = lastUpdatedRange.getUpperBoundAsInstant();
    if (upperBound == null || upperBound.after(getRefreshTime())) return false;
    LoadedFileFilter oldestFilter = getFilters().get(getFilters().size() - 1);
    Date lowerBound = lastUpdatedRange.getLowerBoundAsInstant();
    if (lowerBound == null || lowerBound.before(oldestFilter.getFirstUpdated())) return false;

    List<LoadedFileFilter> filters = getFilters();
    for (LoadedFileFilter filter : filters) {
      if (filter.matchesDateRange(lastUpdatedRange)) {
        if (filter.mightContain(beneficiaryId)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Called periodically to build and refresh the filters list. */
  @Scheduled(fixedDelay = 5000, initialDelay = 10000)
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
     * process will be quick when no loaded file changes are found.
     */
    entityManager.clear(); // Make sure we go back to the DB, not the persistence context
    List<LoadedFile> loadedFiles =
        entityManager
            .createQuery("select f from LoadedFile f order by f.lastUpdated desc", LoadedFile.class)
            .getResultList();
    /**
     * Dev note: refreshTime should be calculated on the pipeline's clock as other dates are. There
     * is the possiblity of clock skew and db replication delay between the pipeline and the data
     * server. Here we add subract a few seconds a safety margin for the possibility of these
     * effects. There methods to do a better estimate, but they are not worth the effort.
     */
    Date localTime = Date.from(Instant.now().minusSeconds(delaySeconds));
    refreshTime =
        loadedFiles.size() > 0 && loadedFiles.get(0).getLastUpdated().after(localTime)
            ? loadedFiles.get(0).getLastUpdated()
            : localTime;

    // Update the filters
    for (LoadedFile loadedFile : loadedFiles) {
      if (!hasFilterFor(loadedFile)) {
        LoadedFileFilter newFilter = buildFilter(loadedFile);
        updateFilters(newFilter);
      }
    }
  }

  /**
   * Refresh filters directly from passed in information. Useful for testing.
   *
   * @param loadedFiles to use
   * @param beneficiaries to use, same for all loaded files
   */
  public void refreshFiltersDirectly(List<LoadedFile> loadedFiles, List<String> beneficiaries) {
    refreshTime = Date.from(Instant.now());

    // Update the filters
    for (LoadedFile loadedFile : loadedFiles) {
      if (!hasFilterFor(loadedFile)) {
        LoadedFileFilter newFilter = buildFilterDirectly(loadedFile, beneficiaries);
        updateFilters(newFilter);
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
   * Does the current list of filters have an associated filter for this loaded file?
   *
   * @param loaded file to find a filter for
   * @return true iff there is a filter associated with this loaded file
   */
  private boolean hasFilterFor(LoadedFile loaded) {
    return filters.stream()
        .anyMatch(
            filter -> {
              return filter.getFileId() == loaded.getFileId()
                  && filter.getLastUpdated() == loaded.getLastUpdated()
                  && filter.getFirstUpdated() == loaded.getFirstUpdated();
            });
  }

  /**
   * Build a filter for this loaded file. Execution time is O(n) of the number of beneficiaries
   * associated with this loaded file.
   *
   * @param loadedFile to build a filter
   * @return a new filter
   */
  private LoadedFileFilter buildFilter(LoadedFile loadedFile) {
    Long beneCount =
        entityManager
            .createQuery(
                "select count(b) from LoadedBeneficiary b where b.fileId = :fileId", Long.class)
            .setParameter("fileId", loadedFile.getFileId())
            .getSingleResult();
    LOGGER.info(
        "Building loaded file filter for loaded file {} with {} beneficiaries",
        loadedFile.getFileId(),
        beneCount);
    BloomFilter<String> bloomFilter =
        BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), beneCount);

    /**
     * Dev note: The number of beneficiaries in a loaded file can be large (>1M). So we could be in
     * this loop for a while. Hibernate's ScrollableResults allows us to add the beneficiaries in
     * small batches. JPA 2.2 has a similar stream feature, but we are not using this package.
     */
    Session session = entityManager.unwrap(Session.class);
    try (ScrollableResults results =
        session
            .createQuery("select b.beneficiaryId from LoadedBeneficiary b where b.fileId = :fileId")
            .setParameter("fileId", loadedFile.getFileId())
            .setCacheMode(CacheMode.IGNORE)
            .setFetchSize(FETCH_SIZE)
            .scroll(ScrollMode.FORWARD_ONLY)) {
      long putCount = 0;
      while (results.next()) {
        String beneficiaryId = results.getText(0);
        bloomFilter.put(beneficiaryId);
        if (++putCount % FETCH_SIZE == 0) {
          // Periodically release memory
          entityManager.flush();
          entityManager.clear();
        }
      }
    }

    LOGGER.info("Finished building filter for file {}", loadedFile.getFileId());
    return new LoadedFileFilter(
        loadedFile.getFileId(),
        loadedFile.getFirstUpdated(),
        loadedFile.getLastUpdated(),
        bloomFilter);
  }

  /**
   * Build a filter with provided data.
   *
   * @param loadedFile the loaded file to build a filter
   * @param beneficiaries to fill the filter
   * @return a new filter
   */
  private LoadedFileFilter buildFilterDirectly(LoadedFile loadedFile, List<String> beneficiaries) {
    BloomFilter<String> bloomFilter =
        BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), beneficiaries.size());
    for (String beneId : beneficiaries) {
      bloomFilter.put(beneId);
    }
    return new LoadedFileFilter(
        loadedFile.getFileId(),
        loadedFile.getFirstUpdated(),
        loadedFile.getLastUpdated(),
        bloomFilter);
  }

  /**
   * Update the filter list with the new filter. If a filter associated with the same loaded file
   * exsits in the the filter list, replace it. Otherwise, add the new filter.
   *
   * @param newFilter to put into the filter list
   */
  private void updateFilters(LoadedFileFilter newFilter) {
    synchronized (this) { // Avoid multiple writer race conditions
      ArrayList<LoadedFileFilter> copy = new ArrayList<>(filters);
      copy.removeIf(filter -> filter.getFileId() == newFilter.getFileId());
      copy.add(newFilter);
      copy.sort((a, b) -> b.getLastUpdated().compareTo(a.getLastUpdated())); // Decsending order
      filters = copy;
    }
  }
}
