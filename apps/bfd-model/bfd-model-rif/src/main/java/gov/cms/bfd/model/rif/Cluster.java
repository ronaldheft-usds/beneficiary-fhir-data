package gov.cms.bfd.model.rif;

import java.time.Instant;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "`Clusters`")
public class Cluster {
  @Id
  @Column(name = "`clusterId`", nullable = false)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "clusters_clusterId_seq")
  @SequenceGenerator(
      name = "clusters_clusterId_seq",
      sequenceName = "clusters_clusterId_seq",
      allocationSize = 10)
  private long clusterId;

  @Column(name = "`fileCount`", nullable = false)
  private int fileCount;

  @Column(name = "`firstUpdated`", nullable = true)
  private Date firstUpdated;

  @Column(name = "`lastUpdated`", nullable = true)
  private Date lastUpdated;

  /**
   * Create an Batch entity
   *
   * @return a new entity
   */
  public static Cluster create() {
    Cluster entity = new Cluster();
    Date nowDate = Date.from(Instant.now());
    entity.setFileCount(1);
    entity.setFirstUpdated(nowDate);
    entity.setLastUpdated(nowDate);
    return entity;
  }

  /** @return the clusterId */
  public long getClusterId() {
    return clusterId;
  }

  /** @param clusterId the clusterId to set */
  public void setClusterId(long clusterId) {
    this.clusterId = clusterId;
  }

  /** @return the fileCount */
  public int getFileCount() {
    return fileCount;
  }

  /** @param fileCount the fileCount to set */
  public void setFileCount(int fileCount) {
    this.fileCount = fileCount;
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
}
