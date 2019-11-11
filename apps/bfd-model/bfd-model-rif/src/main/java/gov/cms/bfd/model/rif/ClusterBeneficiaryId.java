package gov.cms.bfd.model.rif;

import java.io.Serializable;

public class ClusterBeneficiaryId implements Serializable {
  private static final long serialVersionUID = 1L;

  private long clusterId;

  private String beneficiaryId;

  // default constructor
  public ClusterBeneficiaryId() {}

  public ClusterBeneficiaryId(long clusterId, String beneficiaryId) {
    this.clusterId = clusterId;
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
    result = prime * result + (int) (clusterId ^ (clusterId >>> 32));
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
    ClusterBeneficiaryId other = (ClusterBeneficiaryId) obj;
    if (beneficiaryId == null) {
      if (other.beneficiaryId != null) return false;
    } else if (!beneficiaryId.equals(other.beneficiaryId)) return false;
    if (clusterId != other.clusterId) return false;
    return true;
  }
}
