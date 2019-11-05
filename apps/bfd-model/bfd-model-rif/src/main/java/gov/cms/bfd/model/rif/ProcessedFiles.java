package gov.cms.bfd.model.rif;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "`ProcessedFiles`")
public class ProcessedFiles {
  @Id
  @Column(name = "`fileId`", nullable = false)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "processedFiles_fileId_seq")
  @SequenceGenerator(
      name = "processedFiles_fileId_seq",
      sequenceName = "processedFiles_fileId_seq",
      allocationSize = 10)
  private long fileId;

  @Column(name = "`rifType`", nullable = false)
  private String rifType;

  @Column(name = "`sequenceId`", nullable = false)
  private String sequenceId;

  @Column(name = "`startTime`", nullable = true)
  private Date startTime;

  @Column(name = "`endTime`", nullable = true)
  private Date endTime;

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

  /** @return the sequenceId */
  public String getSequenceId() {
    return sequenceId;
  }

  /** @param sequenceId the sequenceId to set */
  public void setSequenceId(String sequenceId) {
    this.sequenceId = sequenceId;
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
