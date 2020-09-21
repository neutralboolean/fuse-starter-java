package org.galatea.starter.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.galatea.starter.utils.AlphaVantageResponseSerializer;

@JsonSerialize(using = AlphaVantageResponseSerializer.class)
@NoArgsConstructor
@Data
public class AlphaVantageResponse {
  private String ticker;
  @JsonProperty(value = "Time Series (Daily)") private List<StockData> prices;
}
