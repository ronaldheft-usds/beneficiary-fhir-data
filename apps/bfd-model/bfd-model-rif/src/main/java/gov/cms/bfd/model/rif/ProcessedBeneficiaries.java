package gov.cms.bfd.model.rif;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

@Entity
@Table(name = "`ProcessedBeneficiaries`")
@IdClass(ProcessedBeneficiariesId.class)
public class ProcessedBeneficiaries {
  @Column(name = "`fileId`", nullable = false)
  @Id
  private long fileId;

  @Column(name = "`beneficiaryId`", nullable = false)
  @Id
  private String beneficiaryId;

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
