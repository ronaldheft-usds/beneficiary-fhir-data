package gov.cms.bfd.model.meta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Class to serialize and deserialize a filter. */
public class FilterSerialization {
  public static final String JAVA_ARRAYLIST_SERIALIZATION = "ArrayList";
  public static final String GZIP_ARRAY_SERIALIZATION = "GZipArray";
  public static final String DEFAULT_SERIALIZATION = JAVA_ARRAYLIST_SERIALIZATION;

  /**
   * Serialize the array of beneficiaries in the current default format
   *
   * @param beneficiaries to serialize
   * @return serialization of the array
   * @throws IOException when there is serialization error
   */
  public static byte[] serialize(String[] beneficiaries) throws IOException {
    return serializeJava(beneficiaries);
  }

  /**
   * Deserialize the bytes into an array of beneficiaries
   *
   * @param filterType the filter serialization type
   * @param filterBytes the bytes to deserialize
   * @return an array of beneficiciary ids
   * @throws IOException if there was some serialization error
   * @throws ClassNotFoundException if there was some serialization error
   */
  public static String[] deserialize(String filterType, byte[] filterBytes)
      throws IOException, ClassNotFoundException {
    return deserializeJava(filterBytes);
  }

  /**
   * Serialize using Java serialization. This is the first serialization technique tried. Forms a
   * baseline
   *
   * @param beneficiaries to serialize
   * @return serialized bytes
   * @throws IOException
   */
  public static byte[] serializeJava(String[] beneficiaries) throws IOException {
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        final ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
      objectStream.writeObject(beneficiaries);
      return byteStream.toByteArray();
    }
  }

  /**
   * Deserialize using Java serialization scheme
   *
   * @param filterBytes to decrypt
   * @return beneficiary ids
   * @throws IOException if there was some serialization error
   * @throws ClassNotFoundException if there was some serialization error
   */
  public static String[] deserializeJava(byte[] filterBytes)
      throws IOException, ClassNotFoundException {
    try (final ByteArrayInputStream byteStream = new ByteArrayInputStream(filterBytes);
        final ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {
      try {
        String[] result = (String[]) objectStream.readObject();
        return result;
      } catch (ClassCastException ex) {
        throw new IOException("Casting exception");
      }
    }
  }

  /**
   * Convert an array to a list of strings
   *
   * @param array to convert
   * @return list of strings
   */
  public static ArrayList<String> fromStrings(String[] array) {
    final ArrayList<String> arrayList = new ArrayList<>(array.length);
    for (String element : array) {
      arrayList.add(String.valueOf(element));
    }
    return arrayList;
  }

  /**
   * Convert an list of strings to an array of longs.
   *
   * @param list to convert
   * @return array of longs
   */
  public static String[] fromList(List<String> list) {
    return list.toArray(new String[list.size()]);
  }
}
