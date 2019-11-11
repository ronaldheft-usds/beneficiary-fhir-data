package gov.cms.bfd.pipeline.rif.load;

import gov.cms.bfd.model.rif.schema.DatabaseTestHelper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Unit tests for {@link gov.cms.bfd.pipeline.rif.load.RifLoader}. */
public final class RifLoaderTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(RifLoaderTest.class);

  /**
   * Runs a couple of fake HICNs through {@link
   * gov.cms.bfd.pipeline.rif.load.RifLoader#computeHicnHash(LoadAppOptions, SecretKeyFactory,
   * String)} to verify that the expected result is produced.
   */
  @Test
  public void computeHicnHash() {
    LoadAppOptions options =
        RifLoaderTestUtils.getLoadOptions(DatabaseTestHelper.getTestDatabase());
    options =
        new LoadAppOptions(
            1000,
            "nottherealpepper".getBytes(StandardCharsets.UTF_8),
            options.getDatabaseUrl(),
            options.getDatabaseUsername(),
            options.getDatabasePassword(),
            options.getLoaderThreads(),
            options.isIdempotencyRequired());
    LOGGER.info(
        "salt/pepper: {}", Arrays.toString("nottherealpepper".getBytes(StandardCharsets.UTF_8)));
    LOGGER.info("hash iterations: {}", 1000);
    SecretKeyFactory secretKeyFactory = RifLoader.createSecretKeyFactory();

    /*
     * These are the two samples from `dev/design-decisions-readme.md` that
     * the frontend and backend both have tests to verify the result of.
     */
    Assert.assertEquals(
        "d95a418b0942c7910fb1d0e84f900fe12e5a7fd74f312fa10730cc0fda230e9a",
        RifLoader.computeHicnHash(options, secretKeyFactory, "123456789A"));
    Assert.assertEquals(
        "6357f16ebd305103cf9f2864c56435ad0de5e50f73631159772f4a4fcdfe39a5",
        RifLoader.computeHicnHash(options, secretKeyFactory, "987654321E"));
  }

  @Test
  public void clusterFiles() {
    RifFilesEvent rifFilesEvent = RifLoaderTestUtils.createDummyFilesEvent();
    RifFileEvent rifFileEvent = rifFilesEvent.getFileEvents().get(0);

    // Test an empty cluster
    List<Cluster> emptyClusters = Arrays.asList();
    Assert.assertTrue(
        "Expected new cluster when none exist.",
        RifLoader.shouldStartCluster(rifFileEvent, emptyClusters));

    // Test a old cluster
    Cluster oldCluster = Cluster.create();
    oldCluster.setFirstUpdated(Date.from(Instant.now().minusSeconds(101 * 3600)));
    oldCluster.setLastUpdated(Date.from(Instant.now().minusSeconds(100 * 3600)));
    List<Cluster> oldClusters = Arrays.asList(oldCluster);
    Assert.assertTrue(
        "Expected new cluster when the current cluster is old.",
        RifLoader.shouldStartCluster(rifFileEvent, oldClusters));

    // Test a full cluster
    Cluster fullCluster = Cluster.create();
    fullCluster.setFirstUpdated(Date.from(Instant.now().minusSeconds(101 * 3600)));
    fullCluster.setLastUpdated(Date.from(Instant.now()));
    List<Cluster> fullClusters = Arrays.asList(fullCluster);
    Assert.assertTrue(
        "Expected new cluster when the current cluster spans a long time.",
        RifLoader.shouldStartCluster(rifFileEvent, fullClusters));

    // Test a young cluster
    Cluster youngCluster = Cluster.create();
    youngCluster.setFirstUpdated(Date.from(Instant.now().minusSeconds(1 * 3600)));
    youngCluster.setLastUpdated(Date.from(Instant.now()));
    List<Cluster> youngClusters = Arrays.asList(youngCluster);
    Assert.assertFalse(
        "Expected continue with current cluster when the current cluster is young.",
        RifLoader.shouldStartCluster(rifFileEvent, youngClusters));
  }
}
