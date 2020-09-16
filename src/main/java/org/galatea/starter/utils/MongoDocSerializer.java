package org.galatea.starter.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.galatea.starter.domain.MongoDocument;

public class MongoDocSerializer extends StdSerializer<MongoDocument> {

  /**
   * Blank default constructor.
   */
  public MongoDocSerializer() {
    this(null);
  }

  /**
   * Constructor for custom serializer for MongoDocument into JSON.
   * @param md MongoDocument
   */
  public MongoDocSerializer(final Class<MongoDocument> md) {
    super(md);
  }

  @Override
  public void serialize(final MongoDocument value, final JsonGenerator gen,
      final SerializerProvider provider) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("date", value.getDate().toString());
    gen.writeNumberField("open", value.getOpen());
    gen.writeNumberField("high", value.getHigh());
    gen.writeNumberField("low", value.getLow());
    gen.writeNumberField("close", value.getClose());
    gen.writeEndObject();
  }
}
