package uk.gov.ons.fwmt.outcomeprocessors.converter.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.common.dto.OutcomeSuperSetDto;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.common.service.OutcomeServiceProcessor;
import uk.gov.ons.census.fwmt.events.component.GatewayEventManager;
import uk.gov.ons.fwmt.outcomeprocessors.config.GatewayOutcomeQueueConfig;
import uk.gov.ons.fwmt.outcomeprocessors.data.GatewayCache;
import uk.gov.ons.fwmt.outcomeprocessors.message.GatewayOutcomeProducer;
import uk.gov.ons.fwmt.outcomeprocessors.service.GatewayCacheService;
import uk.gov.ons.fwmt.outcomeprocessors.template.TemplateCreator;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.*;
import static uk.gov.ons.fwmt.outcomeprocessors.enums.EventType.NEW_ADDRESS_REPORTED;
import static uk.gov.ons.fwmt.outcomeprocessors.utility.SpgUtilityMethods.isDelivered;
import static uk.gov.ons.fwmt.outcomeprocessors.utility.SpgUtilityMethods.regionLookup;

@Component("NEW_UNIT_ADDRESS")
public class NewUnitAddressLinkedProcessor implements OutcomeServiceProcessor {

  @Autowired
  private DateFormat dateFormat;

  @Autowired
  private GatewayOutcomeProducer gatewayOutcomeProducer;

  @Autowired
  private GatewayEventManager gatewayEventManager;

  @Autowired
  private GatewayCacheService gatewayCacheService;

  @Override
  public UUID process(OutcomeSuperSetDto outcome, UUID caseIdHolder, String type) throws GatewayException {
    UUID caseId = (caseIdHolder != null) ? caseIdHolder : outcome.getCaseId();

    gatewayEventManager.triggerEvent(String.valueOf(caseId), PROCESSING_OUTCOME,
        SURVEY_TYPE, type,
        PROCESSOR, "NEW_UNIT_ADDRESS",
        ORIGINAL_CASE_ID, String.valueOf(outcome.getCaseId()),
        SITE_CASE_ID, (outcome.getSiteCaseId() != null ? String.valueOf(outcome.getSiteCaseId()) : "N/A"));

    boolean isDelivered = isDelivered(outcome);
    cacheData(outcome, outcome.getCaseId(), isDelivered);

    String eventDateTime = dateFormat.format(outcome.getEventDate());
    Map<String, Object> root = new HashMap<>();
    root.put("sourceCase", "NEW_UNIT");
    root.put("outcome", outcome);
    root.put("newCaseId", caseId);
    root.put("sourceCaseId", outcome.getSiteCaseId());
    root.put("region", regionLookup(outcome.getOfficerId()));
    root.put("officerId", outcome.getOfficerId());
    root.put("address", outcome.getAddress());
    root.put("eventDate", eventDateTime);
    root.put("surveyType", type);
    root.put("addressLevel", "U");
    if (type.equals("CE") && outcome.getCeDetails() != null && outcome.getCeDetails().getUsualResidents() != null) {
      root.put("usualResidents",Math.max(outcome.getCeDetails().getUsualResidents(),1));
    } else {
      root.put("usualResidents",1);
    }

    String outcomeEvent = TemplateCreator.createOutcomeMessage(NEW_ADDRESS_REPORTED, root);

    gatewayOutcomeProducer.sendOutcome(outcomeEvent, String.valueOf(outcome.getTransactionId()),
        GatewayOutcomeQueueConfig.GATEWAY_ADDRESS_UPDATE_ROUTING_KEY);

    gatewayEventManager.triggerEvent(String.valueOf(caseId), OUTCOME_SENT,
        SURVEY_TYPE, type,
        TEMPLATE_TYPE, NEW_ADDRESS_REPORTED.toString(),
        TRANSACTION_ID, outcome.getTransactionId().toString(),
        ROUTING_KEY, GatewayOutcomeQueueConfig.GATEWAY_ADDRESS_UPDATE_ROUTING_KEY);

    return caseId;
  }

  private void cacheData(OutcomeSuperSetDto outcome, UUID caseId, boolean isDelivered) throws GatewayException {
    GatewayCache cache = gatewayCacheService.getById(String.valueOf(caseId));
    if (cache != null) {
      throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, "Case already exists in cache: {}", caseId);
    }

    gatewayCacheService.save(GatewayCache.builder()
        .caseId(String.valueOf(caseId))
        .delivered(isDelivered)
        .existsInFwmt(false)
        .accessInfo(outcome.getAccessInfo())
        .careCodes(OutcomeSuperSetDto.careCodesToText(outcome.getCareCodes()))
        .build());
  }
}