package gov.cms.bfd.model.rif;

import com.justdavis.karl.misc.exceptions.BadCodeMonkeyException;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

/** Class to build a LoadedFile and serialize and deserialize a filter. Thread safe. */
public class LoadedFileBuilder {
  private static final int capacityIncrement = 100000;
  private Vector<String> beneficiaries;
  private LoadedFile loadedFile;

  /**
   * Create a builder from a particular file event
   *
   * @param loadedFile to start building
   */
  public LoadedFileBuilder(LoadedFile loadedFile) {
    this.loadedFile = loadedFile;
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
   * @throws IOException on serialization errors
   */
  public LoadedFile build() throws IOException {
    if (loadedFile == null || beneficiaries == null) {
      throw new BadCodeMonkeyException("Shouldn't call build twice");
    }
    synchronized (this) {
      final LoadedFile file = loadedFile;
      final String[] array = FilterSerialization.fromList(beneficiaries);
      file.setCount(beneficiaries.size());
      file.setLastUpdated(new Date());
      file.setFilterType(FilterSerialization.DEFAULT_SERIALIZATION);
      file.setFilterBytes(FilterSerialization.serialize(array));
      loadedFile = null;
      beneficiaries = null;
      return file;
    }
  }
}
