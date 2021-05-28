package uk.gov.ons.fwmt.outcomeprocessors.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.ons.census.fwmt.common.data.ce.CENewStandaloneAddress;
import uk.gov.ons.census.fwmt.common.data.ce.CENewUnitAddress;
import uk.gov.ons.census.fwmt.common.data.ce.CEOutcome;
import uk.gov.ons.census.fwmt.common.data.household.HHNewSplitAddress;
import uk.gov.ons.census.fwmt.common.data.household.HHNewStandaloneAddress;
import uk.gov.ons.census.fwmt.common.data.household.HHOutcome;
import uk.gov.ons.census.fwmt.common.data.spg.SPGNewStandaloneAddress;
import uk.gov.ons.census.fwmt.common.data.spg.SPGNewUnitAddress;
import uk.gov.ons.census.fwmt.common.data.spg.SPGOutcome;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OutcomePreprocessingQueueConfig {

  public static final String OUTCOME_PREPROCESSING_QUEUE = "Outcome.Preprocessing";
  public static final String OUTCOME_PREPROCESSING_EXCHANGE = "Outcome.Preprocessing.Exchange";
  public static final String OUTCOME_PREPROCESSING_ROUTING_KEY = "Outcome.Preprocessing.Request";
  public static final String OUTCOME_PREPROCESSING_DLQ = "Outcome.PreprocessingDLQ";

  @Autowired
  private AmqpAdmin amqpAdmin;
  @Value("${app.rabbitmq.rm.prefetchCount}")
  private int prefetchCount;

  @Bean
  @Qualifier("OS_CM")
  public DefaultClassMapper classMapper() {
    DefaultClassMapper classMapper = new DefaultClassMapper();
    Map<String, Class<?>> idClassMapping = new HashMap<>();
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.spg.SPGOutcome", SPGOutcome.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.spg.SPGNewUnitAddress", SPGNewUnitAddress.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.spg.SPGNewStandaloneAddress", SPGNewStandaloneAddress.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.ce.CEOutcome", CEOutcome.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.ce.CENewUnitAddress", CENewUnitAddress.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.ce.CENewStandaloneAddress", CENewStandaloneAddress.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.household.HHOutcome", HHOutcome.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.household.HHNewSplitAddress", HHNewSplitAddress.class);
    idClassMapping.put("uk.gov.ons.census.fwmt.common.data.household.HHNewStandaloneAddress", HHNewStandaloneAddress.class);
    classMapper.setIdClassMapping(idClassMapping);
    classMapper.setTrustedPackages("*");
    return classMapper;
  }

  @Bean
  @Qualifier("OS_MC")
  public MessageConverter jsonMessageConverter(@Qualifier("OS_CM") DefaultClassMapper cm) {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    Jackson2JsonMessageConverter jsonMessageConverter = new Jackson2JsonMessageConverter(objectMapper);
    jsonMessageConverter.setClassMapper(cm);
    return jsonMessageConverter;
  }
}
