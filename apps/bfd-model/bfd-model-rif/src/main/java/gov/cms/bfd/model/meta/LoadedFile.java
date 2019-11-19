package gov.cms.bfd.model.meta;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "`LoadedFiles`")
public class LoadedFile {

  @Id
  @Column(name = "`loadedFileId`", nullable = false)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loadedFiles_loadedFileId_seq")
  @SequenceGenerator(
      name = "loadedFiles_loadedFileId_seq",
      sequenceName = "loadedFiles_loadedFileId_seq",
      allocationSize = 20)
  private long loadedFileId;

  @Column(name = "`rifType`", nullable = false)
  private String rifType;

  @Column(name = "`count`", nullable = false)
  private int count;

  @Column(name = "`filterType`", nullable = false)
  private String filterType;

  @Column(name = "`filterBytes`", nullable = true)
  @Type(type = "org.hibernate.type.BinaryType")
  private byte[] filterBytes;

  @Column(name = "`firstUpdated`", nullable = false)
  private Date firstUpdated;

  @Column(name = "`lastUpdated`", nullable = false)
  private Date lastUpdated;

  public LoadedFile() {}

  /**
   * Create a LoadedFile
   *
   * @param loadedFileId id
   * @param rifType RifFileType
   * @param count of records
   * @param filterType determines the filter serialization
   * @param filterBytes determines the filter data
   * @param firstUpdated first updated date
   * @param lastUpdated last updated date
   */
  public LoadedFile(
      long loadedFileId,
      String rifType,
      int count,
      String filterType,
      byte[] filterBytes,
      Date firstUpdated,
      Date lastUpdated) {
    this.loadedFileId = loadedFileId;
    this.rifType = rifType;
    this.count = count;
    this.filterType = filterType;
    this.filterBytes = filterBytes;
    this.firstUpdated = firstUpdated;
    this.lastUpdated = lastUpdated;
  }

  /** @return the identifier */
  public long getLoadedFileId() {
    return loadedFileId;
  }

  /** @param loadedFileId the identifier to set */
  public void setLoadedFileId(long loadedFileId) {
    this.loadedFileId = loadedFileId;
  }

  /** @return the rifType */
  public String getRifType() {
    return rifType;
  }

  /** @param rifType the rifType to set */
  public void setRifType(String rifType) {
    this.rifType = rifType;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  public String getFilterType() {
    return filterType;
  }

  public void setFilterType(String filterType) {
    this.filterType = filterType;
  }

  public byte[] getFilterBytes() {
    return filterBytes;
  }

  public void setFilterBytes(byte[] filterBytes) {
    this.filterBytes = filterBytes;
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
