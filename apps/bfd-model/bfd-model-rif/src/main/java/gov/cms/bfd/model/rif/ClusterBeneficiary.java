package gov.cms.bfd.model.rif;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@Table(name = "`ClusterBeneficiaries`")
@IdClass(ClusterBeneficiaryId.class)
public class ClusterBeneficiary {
  @Column(name = "`clusterId`", nullable = false)
  @Id
  private long clusterId;

  @Column(name = "`beneficiaryId`", nullable = false)
  @Id
  private String beneficiaryId;

  /**
   * Create an GroupBeneficiary entity
   *
   * @param clusterId associated with the load
   * @param beneficiaryId associated with the load
   * @return a new entity
   */
  public static ClusterBeneficiary create(long clusterId, String beneficiaryId) {
    ClusterBeneficiary entity = new ClusterBeneficiary();
    entity.setClusterId(clusterId);
    entity.setBeneficiaryId(beneficiaryId);
    return entity;
  }

  /** @return the clusterId */
  public long getClusterId() {
    return clusterId;
  }

  /** @param clusterId the GroupId to set */
  public void setClusterId(long clusterId) {
    this.clusterId = clusterId;
  }

  /** @return the beneficiaryId */
  public String getBeneficiaryId() {
    return beneficiaryId;
  }

  /** @param beneficiaryId the beneficiaryId to set */
  public void setBeneficiaryId(String beneficiaryId) {
    this.beneficiaryId = beneficiaryId;
  }
}
