package gov.cms.bfd.model.rif.meta;

import gov.cms.bfd.model.meta.FilterSerialization;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilterSerializationTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(FilterSerializationTest.class);

  @Test
  public void javaSerialization() throws IOException, ClassNotFoundException {
    final String[] testBenes = buildTestIds();
    final Instant start = Instant.now();
    final byte[] bytes = FilterSerialization.serializeJava(testBenes);
    final String[] outBenes = FilterSerialization.deserializeJava(bytes);
    final Instant end = Instant.now();
    Assert.assertEquals("Expected to have same size", testBenes.length, outBenes.length);
    Assert.assertEquals("Expected to have id match", testBenes[10], outBenes[10]);
    Assert.assertEquals("Expected to have id match", testBenes[10000], outBenes[10000]);
    LOGGER.info("Java serialization time: {} millis", Duration.between(start, end).toMillis());
    LOGGER.info("Java serialization size: {} bytes", bytes.length);
  }

  @Test
  public void basicSerialization() throws IOException, ClassNotFoundException {
    final String[] testBenes = buildTestIds();
    final Instant start = Instant.now();
    final byte[] bytes = FilterSerialization.serializeBasic(testBenes);
    final String[] outBenes = FilterSerialization.deserializeBasic(bytes);
    final Instant end = Instant.now();
    Assert.assertEquals("Expected to have same size", testBenes.length, outBenes.length);
    Assert.assertEquals("Expected to have id match", testBenes[10], outBenes[10]);
    Assert.assertEquals("Expected to have id match", testBenes[99999], outBenes[99999]);
    LOGGER.info("Basic serialization time: {} millis", Duration.between(start, end).toMillis());
    LOGGER.info("Basic serialization size: {} bytes", bytes.length);
  }

  @Ignore // Doesn't work
  @Test
  public void gzipSerialization() throws IOException, ClassNotFoundException {
    final String[] testBenes = buildTestIds();
    final Instant start = Instant.now();
    final byte[] bytes = FilterSerialization.serializeGZip(testBenes);
    final String[] outBenes = FilterSerialization.deserializeGZip(bytes);
    final Instant end = Instant.now();
    Assert.assertEquals("Expected to have same size", testBenes.length, outBenes.length);
    Assert.assertEquals("Expected to have id match", testBenes[10], outBenes[10]);
    Assert.assertEquals("Expected to have id match", testBenes[10000], outBenes[10000]);
    LOGGER.info("GZip serialization time: {} millis", Duration.between(start, end).toMillis());
    LOGGER.info("GZip serialization size: {} bytes", bytes.length);
  }

  static String[] buildTestIds() {
    final String[] array = new String[100000];
    final Random random = new Random();
    for (int i = 0; i < array.length; i++) {
      long beneId = random.nextLong() / 10000000; // Reduce the range of values
      array[i] = String.valueOf(beneId);
    }
    return array;
  }
}
