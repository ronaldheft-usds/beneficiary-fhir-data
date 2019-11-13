package gov.cms.bfd.server.war.stu3.providers;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import gov.cms.bfd.model.rif.Cluster;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The ClusterFilterManager monitors the clusters in the database and creates Bloom filters to match
 * these clusters.
 */
@Component
public class ClusterFilterManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterFilterManager.class);
  private static final int FETCH_SIZE =
      1000; // The batch size of beneficiaries to fetch from the ClusterBeneficiaries table

  private List<ClusterFilter> filters = Arrays.asList();
  private EntityManager entityManager;

  /** @return the list of current filters. Newest first. */
  List<ClusterFilter> getFilters() {
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
    LOGGER.info("Set entity");
  }

  /** Called periodically to build and refresh the cluster filters list. */
  @Scheduled(fixedDelay = 5000, initialDelay = 10000)
  void refreshClusterFilters() {
    // Dev note: the pipeline has a process to trim the cluster list to a small number
    List<Cluster> clusters =
        entityManager
            .createQuery("select c from Cluster c order by c.lastUpdated desc", Cluster.class)
            .getResultList();
    for (Cluster cluster : clusters) {
      if (!hasFilterFor(cluster)) {
        ClusterFilter newFilter = buildFilter(cluster);
        updateFilters(newFilter);
      }
    }
  }

  /**
   * Does the current list of filters have an associated filter for this cluster?
   *
   * @param cluster to find a filter for
   * @return true iff there is a filter associated with this cluster
   */
  private boolean hasFilterFor(Cluster cluster) {
    return filters.stream()
        .anyMatch(
            filter -> {
              return filter.getClusterId() == cluster.getClusterId()
                  && filter.getLastUpdated() == cluster.getLastUpdated()
                  && filter.getFirstUpdated() == cluster.getFirstUpdated();
            });
  }

  /**
   * Build a cluster filter for this cluster. Execution time is O(n) of the number of beneficiaries
   * in the cluster.
   *
   * @param cluster the cluster to build a filter
   * @return a new ClusterFilter
   */
  private ClusterFilter buildFilter(Cluster cluster) {
    Long count =
        entityManager
            .createQuery(
                "select count(b) from ClusterBeneficiary b where b.clusterId = :clusterId",
                Long.class)
            .setParameter("clusterId", cluster.getClusterId())
            .getSingleResult();
    LOGGER.info(
        "Building cluster filter for cluster {} with {} beneficiaries",
        cluster.getClusterId(),
        count);
    BloomFilter<String> bloomFilter =
        BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), count);

    // Dev note: The number of beneficiaries in a cluster can be large (>1M). So we could be in this
    // loop
    // for a while. Using ScrollableResults to avoid loading all beneficiaries into memory at once.
    Session session = entityManager.unwrap(Session.class);
    try (ScrollableResults results =
        session
            .createQuery(
                "select b.beneficiaryId from ClusterBeneficiary b where b.clusterId = :clusterId")
            .setParameter("clusterId", cluster.getClusterId())
            .setFetchSize(FETCH_SIZE)
            .scroll(ScrollMode.FORWARD_ONLY)) {
      while (results.next()) {
        String beneficiaryId = results.getText(0);
        bloomFilter.put(beneficiaryId);
      }
    }

    LOGGER.info("Finished cluster filter for cluster {}", cluster.getClusterId());
    return new ClusterFilter(
        cluster.getClusterId(), cluster.getFirstUpdated(), cluster.getLastUpdated(), bloomFilter);
  }

  /**
   * Update the filter list with the new filter. If a filter for the newFilters cluster exsits in
   * the the filter list, replace it. Otherwise, add the new filter.
   *
   * @param newFilter to put into the filter list
   */
  private void updateFilters(ClusterFilter newFilter) {
    synchronized (this) { // Avoid multiple writer race conditions
      ArrayList<ClusterFilter> copy = new ArrayList<>(filters);
      copy.removeIf(filter -> filter.getClusterId() == newFilter.getClusterId());
      copy.add(newFilter);
      copy.sort((a, b) -> b.getLastUpdated().compareTo(a.getLastUpdated())); // Decsending order
      filters = copy;
    }
  }
}
