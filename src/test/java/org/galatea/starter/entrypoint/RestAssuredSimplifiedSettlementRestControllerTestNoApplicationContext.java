package org.galatea.starter.entrypoint;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static java.util.Collections.singletonList;
import static org.galatea.starter.MvcConfig.APPLICATION_EXCEL;
import static org.galatea.starter.MvcConfig.TEXT_CSV;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import junitparams.FileParameters;
import junitparams.JUnitParamsRunner;
import lombok.extern.slf4j.Slf4j;
import org.galatea.starter.ASpringTest;
import org.galatea.starter.MessageTranslationConfig;
import org.galatea.starter.domain.SettlementMission;
import org.galatea.starter.domain.TradeAgreement;
import org.galatea.starter.entrypoint.messagecontracts.SettlementMissionList;
import org.galatea.starter.entrypoint.messagecontracts.SettlementMissionMessage;
import org.galatea.starter.entrypoint.messagecontracts.TradeAgreementMessage;
import org.galatea.starter.entrypoint.messagecontracts.TradeAgreementMessages;
import org.galatea.starter.service.SettlementService;
import org.galatea.starter.testutils.TestDataGenerator;
import org.galatea.starter.utils.translation.ITranslator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.ParameterContentNegotiationStrategy;

@Slf4j
@Import({MessageTranslationConfig.class})
@RunWith(JUnitParamsRunner.class)
public class RestAssuredSimplifiedSettlementRestControllerTestNoApplicationContext
    extends ASpringTest {

  @Value("${mvc.settleMissionPath}")
  private String settleMissionPath;

  @Value("${mvc.getMissionPath}")
  private String getMissionPath;

  @Value("${mvc.getMissionsPath}")
  private String getMissionsPath;

  @Value("${mvc.deleteMissionPath}")
  private String deleteMissionPath;

  @Value("${mvc.updateMissionPath}")
  private String updateMissionPath;

  @Autowired
  private ITranslator<TradeAgreementMessages, List<TradeAgreement>> tradeAgreementTranslator;

  @Autowired
  private ITranslator<SettlementMission, SettlementMissionMessage> settlementMissionTranslator;

  @Autowired
  private ITranslator<SettlementMissionMessage, SettlementMission> settlementMissionMsgTranslator;

  @MockBean
  private SettlementService mockSettlementService;

  @Autowired
  private SettlementRestController settlementRestController;

  private ObjectMapper objectMapper;

  private JacksonTester<TradeAgreementMessages> agreementJsonTester;

  private JacksonTester<List<Long>> missionIdJsonTester;

  private static final Long MISSION_ID_1 = 1091L;

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    JacksonTester.initFields(this, objectMapper);

    Map<String, MediaType> mediaTypes = new HashMap<>();
    mediaTypes.put("json", MediaType.APPLICATION_JSON);
    mediaTypes.put("xml", MediaType.APPLICATION_XML);
    mediaTypes.put("csv", TEXT_CSV);
    mediaTypes.put("xlsx", APPLICATION_EXCEL);

    ParameterContentNegotiationStrategy parameterContentNegotiationStrategy =
        new ParameterContentNegotiationStrategy(mediaTypes);

    ContentNegotiationManager manager =
        new ContentNegotiationManager(parameterContentNegotiationStrategy);

    RestAssuredMockMvc.standaloneSetup(
        MockMvcBuilders.standaloneSetup(settlementRestController).
            addPlaceholderValue("mvc.settleMissionPath", settleMissionPath).
            addPlaceholderValue("mvc.deleteMissionPath", deleteMissionPath).
            addPlaceholderValue("mvc.updateMissionPath", updateMissionPath).
            addPlaceholderValue("mvc.getMissionsPath", getMissionsPath).
            addPlaceholderValue("mvc.getMissionPath", getMissionPath).
            setContentNegotiationManager(manager).
            setMessageConverters(new MappingJackson2HttpMessageConverter(),
                new Jaxb2RootElementHttpMessageConverter()).
            setControllerAdvice(new RestExceptionHandler()));
  }

  @Test
  @FileParameters(value = "src/test/resources/testSettleAgreement.data",
      mapper = JsonTestFileMapper.class)
  public void testSettleAgreement_JSON(final String agreementJson,
      final String expectedMissionIdJson) throws Exception {
    TradeAgreement expectedAgreement = TradeAgreement.builder().instrument("IBM")
        .internalParty("INT-1").externalParty("EXT-1").buySell("B").qty(100d).build();

    log.info("Agreement json to post {}", agreementJson);

    List<Long> expectedMissionIds = missionIdJsonTester.parse(expectedMissionIdJson).getObject();

    List<String> expectedResponseJsonList = expectedMissionIds.stream()
        .map(id -> "/settlementEngine/mission/" + id).collect(Collectors.toList());

    log.info("Expected json response {}", expectedResponseJsonList);

    TradeAgreementMessages agreementMessages = agreementJsonTester.parse(agreementJson).getObject();
    log.info("Agreement objects that the service will expect {}", agreementMessages);

    BDDMockito.given(this.mockSettlementService.spawnMissions(singletonList(expectedAgreement)))
        .willReturn(Sets.newTreeSet(expectedMissionIds));

    given().
        log().ifValidationFails().
        contentType(ContentType.JSON).
        body(agreementJson).
        when().
        post("/settlementEngine?requestId=1234").
        then().
        log().ifValidationFails().
        body("spawnedMissions", equalTo(expectedResponseJsonList)).
        statusCode(200);
  }

  @Test
  public void testSettleAgreement_XML() throws Exception {
    TradeAgreementMessages messages = TradeAgreementMessages.builder().agreement(
        TradeAgreementMessage.builder().instrument("IBM").internalParty("INT-1")
            .externalParty("EXT-1").buySell("B").qty(100d).build())
        .build();

    JAXBContext context = JAXBContext.newInstance(TradeAgreementMessages.class);
    Marshaller m = context.createMarshaller();
    StringWriter writer = new StringWriter();
    m.marshal(messages, writer);
    String xml = writer.toString();

    log.info("Agreement xml to post {}", xml);

    List<Long> expectedMissionIds = Collections.singletonList(MISSION_ID_1);

    String expectedXmlEntry = "/settlementEngine/mission/" + MISSION_ID_1;

    log.info("Expected xml response {}", expectedXmlEntry);

    BDDMockito.given(this.mockSettlementService.spawnMissions(toTradeAgreements(messages)))
        .willReturn(Sets.newTreeSet(expectedMissionIds));

    given().
        log().ifValidationFails().
        contentType(ContentType.XML).
        accept(ContentType.XML).
        body(xml).
        when().
        post("/settlementEngine?requestId=1234").
        then().
        log().ifValidationFails().
        body("settlementResponse.spawnedMission", equalTo(expectedXmlEntry)).
        statusCode(200);
  }

  private List<TradeAgreement> toTradeAgreements(TradeAgreementMessages messages) {
    return tradeAgreementTranslator.translate(messages);
  }

  @Test
  public void testGetMissionFound_JSON() {
    SettlementMission mission = TestDataGenerator.defaultSettlementMissionData().build();
    log.info("Test mission: {}", mission);

    BDDMockito.given(this.mockSettlementService.findMission(MISSION_ID_1))
        .willReturn(Optional.of(mission));

    //mission.getQty needed to be converted to float in order to be properly compared to qty.  Other primitives and types (string, int, double, BigDecimal) were failing.
    //Found answer at https://stackoverflow.com/a/44501724
    given().
        log().ifValidationFails().
        accept(ContentType.JSON).
        when().
        get("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        body("id", is(mission.getId().intValue())).
        body("externalParty", is(mission.getExternalParty())).
        body("instrument", is(mission.getInstrument())).
        body("direction", is(mission.getDirection())).
        body("qty", equalTo(mission.getQty().floatValue())).
        statusCode(200);
  }

  @Test
  public void testGetMissionFound_XML() {
    SettlementMission mission = TestDataGenerator.defaultSettlementMissionData().build();
    log.info("Test mission: {}", mission);

    BDDMockito.given(this.mockSettlementService.findMission(MISSION_ID_1))
        .willReturn(Optional.of(mission));

    given().
        log().ifValidationFails().
        accept(ContentType.XML).
        when().
        get("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        body(hasXPath("//id", is(mission.getId().toString()))).
        body(hasXPath("//externalParty", is(mission.getExternalParty()))).
        body(hasXPath("//instrument", is(mission.getInstrument()))).
        body(hasXPath("//direction", is(mission.getDirection()))).
        body(hasXPath("//qty", is(mission.getQty().toString()))).
        statusCode(200);
  }

  @Test
  public void testGetMissionNotFound() {
    BDDMockito.given(this.mockSettlementService.findMission(MISSION_ID_1))
        .willReturn(Optional.empty());

    given().
        log().ifValidationFails().
        accept(ContentType.JSON).
        when().
        get("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(404);
  }

  @Test
  public void testGetMissionsFound_JSON() throws Exception {
    SettlementMission mission1 = TestDataGenerator.defaultSettlementMissionData()
        .id(1L).build();
    SettlementMission mission2 = TestDataGenerator.defaultSettlementMissionData()
        .id(2L).build();
    List<SettlementMission> missions = Arrays.asList(mission1, mission2);

    BDDMockito.given(this.mockSettlementService.findMissions(Arrays.asList(1L, 2L)))
        .willReturn(Arrays.asList(mission1, mission2));

    given().
        log().ifValidationFails().
        when().
        get("/settlementEngine/missions?ids=1,2&format=json&requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(200).
        content(equalTo(objectMapper.writeValueAsString(new SettlementMissionList(missions))));
  }

  @Test
  public void testGetMissionsFound_XML() {
    SettlementMission mission1 = TestDataGenerator.defaultSettlementMissionData()
        .id(1L).build();
    SettlementMission mission2 = TestDataGenerator.defaultSettlementMissionData()
        .id(2L).build();

    BDDMockito.given(this.mockSettlementService.findMissions(Arrays.asList(1L, 2L)))
        .willReturn(Arrays.asList(mission1, mission2));

    given().
        log().ifValidationFails().
        when().
        get("/settlementEngine/missions?ids=1,2&format=xml&requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(200).
        contentType("application/xml").
        // In XPath, [n] has a higher precedence than //foo, meaning //foo[n] is interpreted as
        // //(foo[n]). What we actually want is (//foo)[n], so write that explicitly.
            body(hasXPath("(//id)[1]", is(mission1.getId().toString()))).
        body(hasXPath("(//externalParty)[1]", is(mission1.getExternalParty()))).
        body(hasXPath("(//instrument)[1]", is(mission1.getInstrument()))).
        body(hasXPath("(//direction)[1]", is(mission1.getDirection()))).
        body(hasXPath("(//qty)[1]", is(String.valueOf(mission1.getQty())))).
        body(hasXPath("(//id)[2]", is(mission2.getId().toString()))).
        body(hasXPath("(//externalParty)[2]", is(mission2.getExternalParty()))).
        body(hasXPath("(//instrument)[2]", is(mission2.getInstrument()))).
        body(hasXPath("(//direction)[2]", is(mission2.getDirection()))).
        body(hasXPath("(//qty)[2]", is(String.valueOf(mission2.getQty()))));
  }

  @Test
  public void testIncorrectlyFormattedAgreement() {
    String expectedMessage = "Incorrectly formatted message.  Please consult the documentation.";

    given().
        log().ifValidationFails().
        contentType(MediaType.APPLICATION_JSON_VALUE).
        body("invalidAgreementBytes").
        when().
        post("/settlementEngine?requestId=1234").
        then().
        log().ifValidationFails().
        body("status", is(HttpStatus.BAD_REQUEST.name())).
        body("message", is(expectedMessage));
  }

  @Test
  public void testDataAccessFailure() {
    DataAccessException exception = new DataAccessException("msg") {
    };
    when(mockSettlementService.findMission(MISSION_ID_1)).thenThrow(exception);

    given().
        log().ifValidationFails().
        accept(ContentType.JSON).
        when().
        get("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(500).
        body("status", is(HttpStatus.INTERNAL_SERVER_ERROR.name())).
        body("message", is("An internal application error occurred."));
  }

  @Test
  public void testUpdateMission() {
    SettlementMission settlementMission = TestDataGenerator.defaultSettlementMissionData().build();
    settlementMission.setId(MISSION_ID_1);

    when(mockSettlementService.missionExists(MISSION_ID_1))
        .thenReturn(true);
    when(mockSettlementService.updateMission(MISSION_ID_1, settlementMission))
        .thenReturn(Optional.of(settlementMission));

    given().
        log().ifValidationFails().
        contentType(MediaType.APPLICATION_JSON_VALUE).
        body(objectMapper.convertValue(settlementMission, JsonNode.class).toString()).
        accept(ContentType.JSON).
        when().
        put("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(200);
  }

  @Test
  public void testUpdateNonExistentMission() {
    SettlementMission settlementMission = TestDataGenerator.defaultSettlementMissionData().build();

    when(mockSettlementService.missionExists(MISSION_ID_1))
        .thenReturn(false);

    given().
        log().ifValidationFails().
        contentType(MediaType.APPLICATION_JSON_VALUE).
        body(objectMapper.convertValue(settlementMission, JsonNode.class).toString()).
        accept(MediaType.APPLICATION_JSON_VALUE).
        when().
        put("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(HttpStatus.NOT_FOUND.value());
  }

  @Test
  public void testUpdateMissionWithWrongVersion() {
    SettlementMission settlementMission = TestDataGenerator.defaultSettlementMissionData().build();
    settlementMission.setId(MISSION_ID_1);

    when(mockSettlementService.missionExists(MISSION_ID_1))
        .thenReturn(true);

    when(mockSettlementService.updateMission(MISSION_ID_1, settlementMission)).thenThrow(
        ObjectOptimisticLockingFailureException.class);

    given().
        log().ifValidationFails().
        contentType(MediaType.APPLICATION_JSON_VALUE).
        body(objectMapper.convertValue(settlementMission, JsonNode.class).toString()).
        accept(MediaType.APPLICATION_JSON_VALUE).
        when().
        put("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(HttpStatus.CONFLICT.value());
  }

  @Test
  public void testDeleteMission() {
    doNothing().when(mockSettlementService).deleteMission(MISSION_ID_1);

    given().
        log().ifValidationFails().
        accept(MediaType.APPLICATION_JSON_VALUE).
        when().
        delete("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(HttpStatus.OK.value());
  }

  @Test
  public void testDeleteFakeMission() {
    doThrow(EmptyResultDataAccessException.class).when(mockSettlementService)
        .deleteMission(MISSION_ID_1);

    given().
        log().ifValidationFails().
        accept(MediaType.APPLICATION_JSON_VALUE).
        when().
        delete("/settlementEngine/mission/" + MISSION_ID_1 + "?requestId=1234").
        then().
        log().ifValidationFails().
        statusCode(HttpStatus.NOT_FOUND.value());
  }

  @Configuration
  @Import(SettlementRestController.class)
  static class PropertyConfig {

    @Bean
    PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() {
      PropertyPlaceholderConfigurer propertyPlaceholderConfigurer =
          new PropertyPlaceholderConfigurer();
      propertyPlaceholderConfigurer.setLocation(new ClassPathResource("application.properties"));
      return propertyPlaceholderConfigurer;
    }
  }
}