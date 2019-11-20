package gov.cms.bfd.model.meta;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

/**
 * Class to serialize and deserialize a filter. A few are implmented for comparison purposes:
 *
 * <p>Dev Note: Using the benchmark of 1M random beneficiary ids:
 *
 * <p>Basic serialization: 135 millis 13379520 bytes
 *
 * <p>Snappy serialization: 356 millis 11913055 bytes
 *
 * <p>GZip serialization: 1296 millis 6427963 bytes
 *
 * <p>Java serialization: 652 millis 15379436 bytes
 */
public class FilterSerialization {
  public static final String JAVA_ARRAYLIST_SERIALIZATION = "ArrayList";
  public static final String BASIC_SERIALIZATION = "Basic";
  public static final String GZIP_SERIALIZATION = "GZip";
  public static final String SNAPPY_SERIALIZATION = "Snappy";
  public static final String DEFAULT_SERIALIZATION = GZIP_SERIALIZATION;

  /**
   * Serialize the array of beneficiaries in the current default format
   *
   * @param beneficiaries to serialize
   * @return serialization of the array
   * @throws IOException when there is serialization error
   */
  public static byte[] serialize(String[] beneficiaries) throws IOException {
    switch (DEFAULT_SERIALIZATION) {
      case JAVA_ARRAYLIST_SERIALIZATION:
        return serializeJava(beneficiaries);
      case BASIC_SERIALIZATION:
        return serializeBasic(beneficiaries);
      case GZIP_SERIALIZATION:
        return serializeGZip(beneficiaries);
      case SNAPPY_SERIALIZATION:
        return serializeSnappy(beneficiaries);
      default:
        throw new IOException("Invalid filterType");
    }
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
    if (filterType == null || filterBytes == null) {
      throw new IllegalArgumentException("Null arguments");
    }
    if (filterBytes.length == 0) {
      return new String[0];
    }
    switch (filterType) {
      case JAVA_ARRAYLIST_SERIALIZATION:
        return deserializeJava(filterBytes);
      case BASIC_SERIALIZATION:
        return deserializeBasic(filterBytes);
      case GZIP_SERIALIZATION:
        return deserializeGZip(filterBytes);
      case SNAPPY_SERIALIZATION:
        return deserializeSnappy(filterBytes);
      default:
        throw new IOException("Invalid filterType");
    }
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
   * Serialize using basic serialization.
   *
   * @param beneficiaries to serialize
   * @return serialized bytes
   * @throws IOException
   */
  public static byte[] serializeBasic(String[] beneficiaries) throws IOException {
    int capacity = beneficiaries.length * 15;
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(capacity)) {
      writeInBasicFormat(byteStream, beneficiaries);
      return byteStream.toByteArray();
    }
  }

  /**
   * Deserialize using a basic scheme.
   *
   * @param filterBytes to decrypt
   * @return beneficiary ids
   * @throws IOException if there was some serialization error
   */
  public static String[] deserializeBasic(byte[] filterBytes) throws IOException {
    try (final ByteArrayInputStream byteStream = new ByteArrayInputStream(filterBytes)) {
      return readInBasicFormat(byteStream);
    }
  }

  /**
   * Serialize using basic serialization plus GZip.
   *
   * @param beneficiaries to serialize
   * @return serialized bytes
   * @throws IOException
   */
  public static byte[] serializeGZip(String[] beneficiaries) throws IOException {
    int capacity = beneficiaries.length * 15;
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(capacity);
        final GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
      writeInBasicFormat(gzipStream, beneficiaries);
      return byteStream.toByteArray();
    }
  }

  /**
   * Deserialize using a basic scheme plus GZip.
   *
   * @param filterBytes to decrypt
   * @return beneficiary ids
   * @throws IOException if there was some serialization error
   */
  public static String[] deserializeGZip(byte[] filterBytes) throws IOException {
    try (final ByteArrayInputStream byteStream = new ByteArrayInputStream(filterBytes);
        final GZIPInputStream gzipStream = new GZIPInputStream(byteStream)) {
      return readInBasicFormat(gzipStream);
    }
  }

  /**
   * Serialize using basic serialization plus Snappy.
   *
   * @param beneficiaries to serialize
   * @return serialized bytes
   * @throws IOException
   */
  public static byte[] serializeSnappy(String[] beneficiaries) throws IOException {
    int capacity = beneficiaries.length * 15;
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(capacity);
        final SnappyOutputStream snappyStream = new SnappyOutputStream(byteStream)) {
      writeInBasicFormat(snappyStream, beneficiaries);
      return byteStream.toByteArray();
    }
  }

  /**
   * Deserialize using a basic scheme plus Snappy.
   *
   * @param filterBytes to decrypt
   * @return beneficiary ids
   * @throws IOException if there was some serialization error
   */
  public static String[] deserializeSnappy(byte[] filterBytes) throws IOException {
    try (final ByteArrayInputStream byteStream = new ByteArrayInputStream(filterBytes);
        final SnappyInputStream snappyStream = new SnappyInputStream(byteStream)) {
      return readInBasicFormat(snappyStream);
    }
  }

  /**
   * Write the beneficiary to the stream in the basic format.
   *
   * @param stream to write to
   * @param beneficiaries the beneficiaries to write
   * @throws IOException if error
   */
  public static void writeInBasicFormat(OutputStream stream, String[] beneficiaries)
      throws IOException {
    try (BufferedWriter bufferedWriter =
        new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
      for (String beneficiary : beneficiaries) {
        bufferedWriter.write(beneficiary, 0, beneficiary.length());
        bufferedWriter.newLine();
      }
      bufferedWriter.flush();
    }
  }

  /**
   * Read the stream and return an array of beneficiaries.
   *
   * @param stream for input
   * @return array of beneficiary ids
   * @throws IOException if error occurs
   */
  public static String[] readInBasicFormat(InputStream stream) throws IOException {
    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    String[] benes = bufferedReader.lines().toArray(String[]::new);
    return benes;
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
