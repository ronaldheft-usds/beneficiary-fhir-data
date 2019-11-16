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
@Table(name = "`LoadedFiles`")
public class LoadedFile {
  @Id
  @Column(name = "`fileId`", nullable = false)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loadedFiles_fileId_seq")
  @SequenceGenerator(
      name = "loadedFiles_fileId_seq",
      sequenceName = "loadedFiles_fileId_seq",
      allocationSize = 10)
  private long fileId;

  @Column(name = "`rifType`", nullable = false)
  private String rifType;

  @Column(name = "`firstUpdated`", nullable = false)
  private Date firstUpdated;

  @Column(name = "`lastUpdated`", nullable = false)
  private Date lastUpdated;

  /**
   * Create a new LoadedFile from a RifFileEvent
   *
   * @return a new entity
   */
  public static LoadedFile from(RifFileEvent fileEvent) {
    LoadedFile entity = new LoadedFile();
    Date nowDate = Date.from(Instant.now());
    entity.setFirstUpdated(nowDate);
    entity.setLastUpdated(nowDate);
    entity.setRifType(fileEvent.getFile().getFileType().toString());
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

  /** @return the rifType */
  public String getRifType() {
    return rifType;
  }

  /** @param rifType the rifType to set */
  public void setRifType(String rifType) {
    this.rifType = rifType;
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
