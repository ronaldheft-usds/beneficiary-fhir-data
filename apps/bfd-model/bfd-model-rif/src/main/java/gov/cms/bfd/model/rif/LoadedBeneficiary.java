package gov.cms.bfd.model.rif;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@Table(name = "`LoadedBeneficiaries`")
@IdClass(LoadedBeneficiaryId.class)
public class LoadedBeneficiary {
  @Column(name = "`fileId`", nullable = false)
  @Id
  private long fileId;

  @Column(name = "`beneficiaryId`", nullable = false)
  @Id
  private String beneficiaryId;

  /**
   * Create an LoadedBeneficiary entity
   *
   * @param fileId associated with the load
   * @param beneficiaryId associated with the load
   * @return a new entity
   */
  public static LoadedBeneficiary create(long fileId, String beneficiaryId) {
    LoadedBeneficiary entity = new LoadedBeneficiary();
    entity.setFileId(fileId);
    entity.setBeneficiaryId(beneficiaryId);
    return entity;
  }

  /** @return the fileId */
  public long getFileId() {
    return fileId;
  }

  /** @param fileId the fileId to set */
  public void setFileId(long fileId) {
    this.fileId = fileId;
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
