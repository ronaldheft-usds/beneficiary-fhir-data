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

  @Column(name = "`manifestTime`", nullable = false)
  private Date manifestTime;

  @Column(name = "`startTime`", nullable = true)
  private Date startTime;

  @Column(name = "`endTime`", nullable = true)
  private Date endTime;

  /**
   * Create a LoadedFile structure from a rifFileEvent
   *
   * @param rifFileEvent to use
   * @return a new loadedFile structure
   */
  public static LoadedFile from(RifFileEvent rifFileEvent) {
    LoadedFile loadedFile = new LoadedFile();
    loadedFile.setRifType(rifFileEvent.getFile().getFileType().toString());
    loadedFile.setManifestTime(Date.from(rifFileEvent.getParentFilesEvent().getTimestamp()));
    loadedFile.setStartTime(Date.from(Instant.now()));
    return loadedFile;
  }

  /** @return the fileId */
  public long getFileId() {
    return fileId;
  }

  /** @return the rifType */
  public String getRifType() {
    return rifType;
  }

  /** @param rifType the rifType to set */
  public void setRifType(String rifType) {
    this.rifType = rifType;
  }

  /** @return the manifestTime */
  public Date getManifestTime() {
    return manifestTime;
  }

  /** @param manifestTime the manifestTime to set */
  public void setManifestTime(Date manifestTime) {
    this.manifestTime = manifestTime;
  }

  /** @return the startTime */
  public Date getStartTime() {
    return startTime;
  }

  /** @param startTime the startTime to set */
  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }

  /** @return the endTime */
  public Date getEndTime() {
    return endTime;
  }

  /** @param endTime the endTime to set */
  public void setEndTime(Date endTime) {
    this.endTime = endTime;
  }
}
