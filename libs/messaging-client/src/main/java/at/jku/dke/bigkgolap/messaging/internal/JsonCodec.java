package at.jku.dke.bigkgolap.messaging.internal;

import at.jku.dke.bigkgolap.messaging.MessagingDecodeException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonCodec {

  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private JsonCodec() {}

  public static byte[] encode(Object value) {
    try {
      return MAPPER.writeValueAsBytes(value);
    } catch (Exception e) {
      throw new MessagingDecodeException("Failed to encode " + value.getClass().getSimpleName(), e);
    }
  }

  public static <T> T decode(byte[] bytes, Class<T> type) {
    try {
      return MAPPER.readValue(bytes, type);
    } catch (Exception e) {
      throw new MessagingDecodeException("Failed to decode " + type.getSimpleName(), e);
    }
  }
}
