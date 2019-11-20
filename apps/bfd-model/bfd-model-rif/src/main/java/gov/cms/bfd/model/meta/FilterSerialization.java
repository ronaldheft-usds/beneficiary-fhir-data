package gov.cms.bfd.model.meta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;

/** Class to serialize and deserialize a filter. */
public class FilterSerialization {
  public static final String JAVA_ARRAYLIST_SERIALIZATION = "ArrayList";
  public static final String BASIC_SERIALIZATION = "Basic";
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
    if (filterType.equalsIgnoreCase(JAVA_ARRAYLIST_SERIALIZATION)) {
      return deserializeJava(filterBytes);
    } else if (filterType.equalsIgnoreCase(BASIC_SERIALIZATION)) {
      return deserializeBasic(filterBytes);
    } else {
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
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
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
    Deflater deflater = new Deflater(5, false);
    deflater.reset();
    try (final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        final DeflaterOutputStream gzipStream = new DeflaterOutputStream(byteStream, deflater)) {
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
    Deflater deflater = new Deflater(5, false);
    deflater.reset();
    try (final ByteArrayInputStream byteStream = new ByteArrayInputStream(filterBytes);
        final DeflaterInputStream gzipStream = new DeflaterInputStream(byteStream, deflater)) {
      return readInBasicFormat(gzipStream);
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
    // Header
    stream.write(0x55);
    stream.write(0xCC);
    // Number of ids
    int count = beneficiaries.length;
    stream.write(count & 0xFF);
    stream.write((count >> 8) & 0xFF);
    stream.write((count >> 16) & 0xFF);
    stream.write((count >> 24) & 0xFF);
    // Strings
    for (String beneId : beneficiaries) {
      byte[] beneBytes = beneId.getBytes(StandardCharsets.UTF_8);
      // String length
      if (beneBytes.length >= 255) {
        throw new IOException("Beneficiary id is longer than 255 characters");
      }
      stream.write(beneBytes.length & 0xFF);
      // Value
      stream.write(beneBytes);
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
    // Check header
    int header0 = stream.read();
    int header1 = stream.read();
    if (header0 != 0x55 || header1 != 0xCC) {
      throw new IOException("Invalid header");
    }
    // Length
    int count0 = stream.read();
    int count1 = stream.read();
    int count2 = stream.read();
    int count3 = stream.read();
    if (count0 == -1 || count1 == -1 || count2 == -1 || count3 == -1) {
      throw new EOFException("Invalid count");
    }
    int count = count0 + (count1 << 8) + (count2 << 16) + (count3 << 24);
    // Strings
    String[] output = new String[count];
    byte[] buffer = new byte[256];
    for (int i = 0; i < count; i++) {
      // Length
      int length = stream.read();
      if (length == -1) {
        throw new EOFException("Truncated stream");
      }
      // Value
      if (stream.read(buffer, 0, length) == -1) {
        throw new EOFException("Truncated stream");
      }
      output[i] = new String(buffer, 0, length, StandardCharsets.UTF_8);
    }
    return output;
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
