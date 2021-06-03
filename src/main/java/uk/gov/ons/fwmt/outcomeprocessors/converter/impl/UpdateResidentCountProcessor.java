package uk.gov.ons.fwmt.outcomeprocessors.converter.impl;

import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.ORIGINAL_CASE_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.OUTCOME_SENT;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.PROCESSING_OUTCOME;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.PROCESSOR;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.ROUTING_KEY;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.SITE_CASE_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.SURVEY_TYPE;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.TEMPLATE_TYPE;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.TRANSACTION_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.enums.EventType.FIELD_CASE_UPDATED;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.ons.census.fwmt.common.dto.OutcomeSuperSetDto;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.common.service.OutcomeServiceProcessor;
import uk.gov.ons.census.fwmt.events.component.GatewayEventManager;
import uk.gov.ons.fwmt.outcomeprocessors.config.GatewayOutcomeQueueConfig;
import uk.gov.ons.fwmt.outcomeprocessors.message.GatewayOutcomeProducer;
import uk.gov.ons.fwmt.outcomeprocessors.template.TemplateCreator;

@Component("UPDATE_RESIDENT_COUNT")
public class UpdateResidentCountProcessor implements OutcomeServiceProcessor {

  @Autowired
  private DateFormat dateFormat;

  @Autowired
  private GatewayOutcomeProducer gatewayOutcomeProducer;

  @Autowired
  private GatewayEventManager gatewayEventManager;

//  @Autowired
//  private GatewayCacheService gatewayCacheService;

  @Override
  public UUID process(OutcomeSuperSetDto outcome, UUID caseIdHolder, String type) throws GatewayException {
    UUID caseId = (caseIdHolder != null) ? caseIdHolder : outcome.getCaseId();

    gatewayEventManager.triggerEvent(String.valueOf(caseId), PROCESSING_OUTCOME,
    SURVEY_TYPE, type,
    PROCESSOR, "UPDATE_RESIDENT_COUNT",
    ORIGINAL_CASE_ID, String.valueOf(outcome.getCaseId()),
    SITE_CASE_ID, (outcome.getSiteCaseId() != null ? String.valueOf(outcome.getSiteCaseId()) : "N/A"));

    if (outcome.getCeDetails() == null) return caseId;
    if (outcome.getCeDetails().getUsualResidents() == null) return caseId;

    String eventDateTime = dateFormat.format(outcome.getEventDate());
    Map<String, Object> root = new HashMap<>();
    root.put("outcome", outcome);
    root.put("eventDate", eventDateTime);
    root.put("caseId", caseId);
    root.put("usualResidents", outcome.getCeDetails().getUsualResidents());

    String outcomeEvent = TemplateCreator.createOutcomeMessage(FIELD_CASE_UPDATED, root);

//    GatewayCache cache = gatewayCacheService.getById(String.valueOf(caseId));

//    if (cache != null && ("CANCEL".equals(cache.lastActionInstruction) || "CANCEL(HELD)".equals(cache.lastActionInstruction))) {
//      gatewayCacheService.save(cache.toBuilder()
//          .lastActionInstruction(ActionInstructionType.UPDATE.toString())
//          .build());
//    }

    gatewayOutcomeProducer.sendOutcome(outcomeEvent, String.valueOf(outcome.getTransactionId()),
        GatewayOutcomeQueueConfig.GATEWAY_FIELD_CASE_UPDATE_ROUTING_KEY);

    gatewayEventManager.triggerEvent(String.valueOf(caseId), OUTCOME_SENT,
        SURVEY_TYPE, type,
        TEMPLATE_TYPE, FIELD_CASE_UPDATED.toString(),
        TRANSACTION_ID, outcome.getTransactionId().toString(),
        ROUTING_KEY, GatewayOutcomeQueueConfig.GATEWAY_FIELD_CASE_UPDATE_ROUTING_KEY);
    return caseId;
  }
}
