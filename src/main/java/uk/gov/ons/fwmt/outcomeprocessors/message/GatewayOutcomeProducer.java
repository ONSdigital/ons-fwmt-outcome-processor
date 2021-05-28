package uk.gov.ons.fwmt.outcomeprocessors.message;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.fwmt.outcomeprocessors.config.GatewayOutcomeQueueConfig;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class GatewayOutcomeProducer {

  @Autowired
  @Qualifier("OS_RT_RM")
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Retryable
  public void sendOutcome(String outcomeEvent, String transactionId, String routingKey) throws GatewayException {
    MessageProperties messageProperties = new MessageProperties();
    messageProperties.setContentType("application/json");
    long epochMilli = Instant.now().toEpochMilli();
    messageProperties.setTimestamp(new Date(epochMilli));
    MessageConverter messageConverter = new Jackson2JsonMessageConverter();

    objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(),
        true);

    try {
      Message message = messageConverter.toMessage(objectMapper.readTree(outcomeEvent), messageProperties);

      rabbitTemplate.convertAndSend(GatewayOutcomeQueueConfig.GATEWAY_OUTCOME_EXCHANGE, routingKey, message);
    } catch (IOException e) {
      throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, e,
          "Cannot process address update for transaction ID " + transactionId + "msg: " + outcomeEvent);
    }
  }
}
