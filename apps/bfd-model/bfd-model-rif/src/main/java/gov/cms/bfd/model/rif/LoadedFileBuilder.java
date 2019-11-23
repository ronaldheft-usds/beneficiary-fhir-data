package gov.cms.bfd.model.rif;

import java.io.IOException;
import java.util.Date;
import java.util.Vector;

/** Class to build a LoadedFile and serialize and deserialize a filter. Thread safe. */
public class LoadedFileBuilder {
  private static final int capacityIncrement = 100000;
  private Vector<String> beneficiaries;
  private Date startTime;
  private String rifFileType;

  /**
   * Create a builder from a particular file event
   *
   * @param rifFileEvent to base the LoadedFile
   */
  public LoadedFileBuilder(RifFileEvent rifFileEvent) {
    this.rifFileType = rifFileEvent.getFile().getFileType().toString();
    this.startTime = new Date();
    this.beneficiaries = new Vector<>(capacityIncrement, capacityIncrement);
  }

  /**
   * Associate a beneficiaryId with this LoadedFile
   *
   * @param beneficiaryId to put in the filter
   */
  public void associateBeneficiary(String beneficiaryId) {
    if (beneficiaryId == null || beneficiaryId.isEmpty()) {
      throw new IllegalArgumentException("Null or empty beneficiary");
    }
    beneficiaries.add(beneficiaryId);
  }

  /**
   * Create a LoadedFile from the data in the builder
   *
   * @return a new LoadedFile
   * @throws IOException
   */
  public LoadedFile build() throws IOException {
    synchronized (this) {
      final LoadedFile file = new LoadedFile();
      final String[] array = FilterSerialization.fromList(beneficiaries);
      file.setRifType(rifFileType);
      file.setCount(beneficiaries.size());
      file.setFirstUpdated(startTime);
      file.setLastUpdated(new Date());
      file.setFilterType(FilterSerialization.DEFAULT_SERIALIZATION);
      file.setFilterBytes(FilterSerialization.serialize(array));
      return file;
    }
  }
}
