package gov.cms.bfd.model.meta;

import gov.cms.bfd.model.rif.RifFileEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

/** Class to build a LoadedFile and serialize and deserialize a filter. */
public class LoadedFileBuilder {
  private ArrayList<String> beneficiaries;
  private Date startTime;
  private String rifFileType;

  /**
   * Create a builder from a particular file event
   *
   * @param rifFileEvent to base the LoadedFile
   */
  public LoadedFileBuilder(RifFileEvent rifFileEvent) {
    this.rifFileType = rifFileEvent.getFile().getFileType().toString();
    this.startTime = Date.from(Instant.now());
    this.beneficiaries = new ArrayList<>();
  }

  /**
   * Associate a beneficiaryId with this LoadedFile
   *
   * @param beneficiaryId to put in the filter
   */
  public void associateBeneficiary(String beneficiaryId) throws NumberFormatException {
    beneficiaries.add(beneficiaryId);
  }

  /**
   * Create a LoadedFile from the data in the builder
   *
   * @return a new LoadedFile
   * @throws IOException
   */
  public LoadedFile build() throws IOException {
    final LoadedFile file = new LoadedFile();
    final String[] array = FilterSerialization.fromList(beneficiaries);
    file.setRifType(rifFileType);
    file.setCount(beneficiaries.size());
    file.setFirstUpdated(startTime);
    file.setLastUpdated(Date.from(Instant.now()));
    file.setFilterType(FilterSerialization.DEFAULT_SERIALIZATION);
    file.setFilterBytes(FilterSerialization.serialize(array));
    return file;
  }
}
