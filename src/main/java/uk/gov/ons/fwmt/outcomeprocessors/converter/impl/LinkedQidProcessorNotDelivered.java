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
import static uk.gov.ons.fwmt.outcomeprocessors.enums.EventType.QUESTIONNAIRE_LINKED;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.ons.census.fwmt.common.dto.FulfilmentRequestDto;
import uk.gov.ons.census.fwmt.common.dto.OutcomeSuperSetDto;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.common.service.OutcomeServiceProcessor;
import uk.gov.ons.census.fwmt.events.component.GatewayEventManager;
import uk.gov.ons.fwmt.outcomeprocessors.config.GatewayOutcomeQueueConfig;
import uk.gov.ons.fwmt.outcomeprocessors.message.GatewayOutcomeProducer;
import uk.gov.ons.fwmt.outcomeprocessors.template.TemplateCreator;

@Component("LINKED_QID_NOT_DELIVERED")
@Transactional
public class LinkedQidProcessorNotDelivered implements OutcomeServiceProcessor {

  @Autowired
  private DateFormat dateFormat;

  @Autowired
  private GatewayOutcomeProducer gatewayOutcomeProducer;

  @Autowired
  private GatewayEventManager gatewayEventManager;

//  @Autowired
//  private GatewayCacheService gatewayCacheService;

  private boolean isQuestionnaireLinked(FulfilmentRequestDto fulfilmentRequest) {
    return (fulfilmentRequest.getQuestionnaireID() != null);
  }

  @Override
  public UUID process(OutcomeSuperSetDto outcome, UUID caseIdHolder, String type) throws GatewayException {
    gatewayEventManager.triggerEvent(String.valueOf(outcome.getCaseId()), PROCESSING_OUTCOME,
        SURVEY_TYPE, type,
        PROCESSOR, "LINKED_QID_NOT_DELIVERED",
        ORIGINAL_CASE_ID, String.valueOf(outcome.getCaseId()),
        SITE_CASE_ID, (outcome.getSiteCaseId() != null ? String.valueOf(outcome.getSiteCaseId()) : "N/A"));


    if (outcome.getFulfilmentRequests() == null) return caseIdHolder;
    UUID caseId = (caseIdHolder != null) ? caseIdHolder : outcome.getCaseId();
    for (FulfilmentRequestDto fulfilmentRequest : outcome.getFulfilmentRequests()) {
      if (isQuestionnaireLinked(fulfilmentRequest)) {
        String eventDateTime = dateFormat.format(outcome.getEventDate());

        Map<String, Object> root = new HashMap<>();
        root.put("outcome", outcome);
        root.put("caseId", caseId);
        root.put("questionnaireId", fulfilmentRequest.getQuestionnaireID());
        root.put("eventDate", eventDateTime);
//        cacheData(caseId);

        String outcomeEvent = TemplateCreator.createOutcomeMessage(QUESTIONNAIRE_LINKED, root);

        gatewayOutcomeProducer.sendOutcome(outcomeEvent, String.valueOf(outcome.getTransactionId()),
            GatewayOutcomeQueueConfig.GATEWAY_QUESTIONNAIRE_UPDATE_ROUTING_KEY);

        gatewayEventManager.triggerEvent(String.valueOf(caseId), OUTCOME_SENT,
            SURVEY_TYPE, type,
            TEMPLATE_TYPE, QUESTIONNAIRE_LINKED.toString(),
            TRANSACTION_ID, outcome.getTransactionId().toString(),
            ROUTING_KEY, GatewayOutcomeQueueConfig.GATEWAY_QUESTIONNAIRE_UPDATE_ROUTING_KEY);
      }
    }
    return caseIdHolder;
  }

//  private void cacheData(UUID caseId) {
//    GatewayCache cache = gatewayCacheService.getById(String.valueOf(caseId));
//    GatewayCacheBuilder builder;
//    if (cache == null) builder = GatewayCache.builder();
//    else builder = cache.toBuilder();
//
//    gatewayCacheService.save(builder
//        .caseId(String.valueOf(caseId))
//        .delivered(false)
//        .build());
//  }
}
