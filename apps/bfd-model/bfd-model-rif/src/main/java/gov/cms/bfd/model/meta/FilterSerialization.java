package gov.cms.bfd.model.meta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

/** Class to serialize and deserialize a filter. */
public class FilterSerialization {
  public static final String ARRAY_LIST_SERIALIZATION = "ArrayList";

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
