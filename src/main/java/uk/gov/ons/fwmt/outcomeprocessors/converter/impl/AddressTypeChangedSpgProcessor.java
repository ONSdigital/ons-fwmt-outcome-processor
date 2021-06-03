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
import static uk.gov.ons.fwmt.outcomeprocessors.enums.EventType.ADDRESS_TYPE_CHANGED;

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

@Component("ADDRESS_TYPE_CHANGED_SPG")
public class AddressTypeChangedSpgProcessor implements OutcomeServiceProcessor {

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
        PROCESSOR, "ADDRESS_TYPE_CHANGED_SPG",
        ORIGINAL_CASE_ID, String.valueOf(outcome.getCaseId()),
        SITE_CASE_ID, (outcome.getSiteCaseId() != null ? String.valueOf(outcome.getSiteCaseId()) : "N/A"));

    Map<String, Object> root = new HashMap<>();
    root.put("caseId", caseId);
    UUID newCaseId = UUID.randomUUID();
    root.put("newCaseId", newCaseId);
//    cacheData(outcome, caseId, newCaseId);

    String eventDateTime = dateFormat.format(outcome.getEventDate());
    root.put("outcome", outcome);
    root.put("eventDate", eventDateTime);
    root.put("surveyType", "SPG");
    root.put("estabType", outcome.getCeDetails() != null && outcome.getCeDetails().getEstablishmentType() != null ?
        outcome.getCeDetails().getEstablishmentType() : null);
    root.put("estabName", outcome.getCeDetails() != null && outcome.getCeDetails().getEstablishmentName() != null ?
        outcome.getCeDetails().getEstablishmentName() : null);

    if (outcome.getCeDetails() == null || outcome.getCeDetails().getUsualResidents() == null) {
      root.put("usualResidents", 0);
    } else {
      root.put("usualResidents", outcome.getCeDetails().getUsualResidents());
    }

    String outcomeEvent = TemplateCreator.createOutcomeMessage(ADDRESS_TYPE_CHANGED, root);

    gatewayOutcomeProducer.sendOutcome(outcomeEvent, String.valueOf(outcome.getTransactionId()),
        GatewayOutcomeQueueConfig.GATEWAY_ADDRESS_UPDATE_ROUTING_KEY);

    gatewayEventManager.triggerEvent(String.valueOf(caseId), OUTCOME_SENT,
        SURVEY_TYPE, type,
        TEMPLATE_TYPE, ADDRESS_TYPE_CHANGED.toString(),
        TRANSACTION_ID, outcome.getTransactionId().toString(),
        ROUTING_KEY, GatewayOutcomeQueueConfig.GATEWAY_ADDRESS_UPDATE_ROUTING_KEY);

    return newCaseId;
  }

//  private void cacheData(OutcomeSuperSetDto outcome, UUID caseId, UUID newCaseId) throws GatewayException {
//    GatewayCache parentCacheJob = gatewayCacheService.getById(caseId.toString());
//    if (parentCacheJob == null) {
//      throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, "Parent case does not exist in cache: {}", caseId);
//    }
//
//    GatewayCache newCachedJob = gatewayCacheService.getById(newCaseId.toString());
//    if (newCachedJob != null) {
//      throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, "New case exists in cache: {}", caseId);
//    }
//
//    gatewayCacheService.save(GatewayCache.builder()
//        .caseId(newCaseId.toString())
//        .existsInFwmt(false)
//        .accessInfo(outcome.getAccessInfo())
//        .careCodes(OutcomeSuperSetDto.careCodesToText(outcome.getCareCodes()))
//        .build());
//  }
}
