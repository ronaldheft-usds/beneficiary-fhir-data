package gov.cms.bfd.model.rif;

import java.io.Serializable;

public class LoadedBeneficiaryId implements Serializable {
  private static final long serialVersionUID = 1L;

  private long fileId;

  private String beneficiaryId;

  // default constructor
  public LoadedBeneficiaryId() {}

  public LoadedBeneficiaryId(long fileId, String beneficiaryId) {
    this.fileId = fileId;
    this.beneficiaryId = beneficiaryId;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((beneficiaryId == null) ? 0 : beneficiaryId.hashCode());
    result = prime * result + (int) (fileId ^ (fileId >>> 32));
    return result;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    LoadedBeneficiaryId other = (LoadedBeneficiaryId) obj;
    if (beneficiaryId == null) {
      if (other.beneficiaryId != null) return false;
    } else if (!beneficiaryId.equals(other.beneficiaryId)) return false;
    if (fileId != other.fileId) return false;
    return true;
  }
}
