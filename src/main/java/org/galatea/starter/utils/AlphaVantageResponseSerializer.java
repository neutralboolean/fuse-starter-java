package org.galatea.starter.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.galatea.starter.domain.AlphaVantageResponse;
import org.galatea.starter.domain.StockData;

public class AlphaVantageResponseSerializer extends StdSerializer<AlphaVantageResponse> {

  /**
   * Blank default constructor.
   */
  public AlphaVantageResponseSerializer() {
    this(null);
  }

  /**
   * Constructor for custom serializer for AlphaVantageResponse into JSON.
   * @param md MongoDocument
   */
  public AlphaVantageResponseSerializer(final Class<AlphaVantageResponse> md) {
    super(md);
  }

  @Override
  public void serialize(final AlphaVantageResponse value, final JsonGenerator gen,
      final SerializerProvider provider) throws IOException {
    gen.writeStartObject();
    gen.writeString(String.format("Results for %s", value.getTicker()));
    gen.writeObject(value.getPrices());
    gen.writeEndObject();
  }
}
