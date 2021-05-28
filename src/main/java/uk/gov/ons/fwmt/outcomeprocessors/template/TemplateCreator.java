package uk.gov.ons.fwmt.outcomeprocessors.template;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.common.service.OutcomeServiceProcessor;
import uk.gov.ons.fwmt.outcomeprocessors.enums.EventType;

import java.io.StringWriter;
import java.util.Map;

@Slf4j
@Component
public class TemplateCreator {

  public static String createOutcomeMessage(EventType eventType, Map<String, Object> root) throws GatewayException {
    String outcomeMessage = "";

    try {
      Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
      configuration.setClassForTemplateLoading(OutcomeServiceProcessor.class, "/templates/");
      configuration.setDefaultEncoding("UTF-8");
      configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
      configuration.setLogTemplateExceptions(false);
      configuration.setWrapUncheckedExceptions(true);

      Template temp = configuration.getTemplate("payload/" + eventType.name() + "-event.ftl");
      try (StringWriter out = new StringWriter(); StringWriter outcomeEventMessage = new StringWriter()) {

        temp.process(root, out);

        out.flush();
        String outcomePayload = out.toString();

        root.put("payload", outcomePayload);
        root.put("eventType", eventType);
        Template outcomeMessageTemplate = configuration.getTemplate("rm-outcome-event.ftl");
        outcomeMessageTemplate.process(root, outcomeEventMessage);

        outcomeEventMessage.flush();
        outcomeMessage = outcomeEventMessage.toString();

      } catch (Exception e) {
        log.error("Error: ", e);

        throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, "Problem creating Outcome Message", e);
      }
    } catch (Exception e) {
      log.error("Error: ", e);
      throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, "Problem creating Outcome Message", e);
    }

    return outcomeMessage;
  }
}
