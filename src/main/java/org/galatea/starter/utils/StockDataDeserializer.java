package org.galatea.starter.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import org.galatea.starter.domain.StockData;

public class StockDataDeserializer extends StdDeserializer<StockData> {

  public StockDataDeserializer() {
    this(null);
  }

  public StockDataDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public StockData deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    return null;
  }
}
