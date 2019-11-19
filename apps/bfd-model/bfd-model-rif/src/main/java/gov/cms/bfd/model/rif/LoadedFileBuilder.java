package gov.cms.bfd.model.rif;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

/** Class to build a LoadedFile and serialize and deserialize a filter. */
public class LoadedFileBuilder {
  private ArrayList<String> beneficiaries;
  private Date startTime;
  private RifFileType rifFileType;

  public LoadedFileBuilder(RifFileEvent rifFileEvent) {
    this.rifFileType = rifFileEvent.getFile().getFileType();
    this.startTime = Date.from(Instant.now());
    this.beneficiaries = new ArrayList<>();
  }

  public void associateBeneficiary(String beneficiaryId) {
    beneficiaries.add(beneficiaryId);
  }

  public LoadedFile build() throws IOException {
    final LoadedFile file = new LoadedFile();
    file.setRifType(rifFileType.toString());
    file.setCount(beneficiaries.size());
    file.setFirstUpdated(startTime);
    file.setLastUpdated(Date.from(Instant.now()));
    file.setFilterType(LoadedFile.ARRAY_LIST_SERIALIZATION);
    file.setFilterBytes(serializeBeneficiaries(beneficiaries));
    return file;
  }

  public static byte[] serializeBeneficiaries(ArrayList<String> beneficiaries) throws IOException {
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        final ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
      objectStream.writeObject(beneficiaries);
      return byteStream.toByteArray();
    }
  }

  public static ArrayList<String> deserializeBeneficiaries(byte[] filterBytes)
      throws IOException, ClassNotFoundException {
    try (final ByteArrayInputStream byteStream = new ByteArrayInputStream(filterBytes);
        final ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {
      try {
        @SuppressWarnings("unchecked")
        ArrayList<String> result = (ArrayList<String>) objectStream.readObject();
        return result;
      } catch (ClassCastException ex) {
        throw new IOException("Casting exception");
      }
    }
  }
}
