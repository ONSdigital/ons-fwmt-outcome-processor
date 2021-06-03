package uk.gov.ons.fwmt.outcomeprocessors.converter.impl;

import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.ORIGINAL_CASE_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.OUTCOME_SENT;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.PROCESSING_OUTCOME;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.PROCESSOR;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.ROUTING_KEY;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.SURVEY_TYPE;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.TEMPLATE_TYPE;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.TRANSACTION_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.enums.EventType.NEW_ADDRESS_REPORTED;
import static uk.gov.ons.fwmt.outcomeprocessors.utility.SpgUtilityMethods.isDelivered;
import static uk.gov.ons.fwmt.outcomeprocessors.utility.SpgUtilityMethods.regionLookup;

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

@Component("NEW_ADDRESS_REPORTED")
public class NewAddressReportedProcessor implements OutcomeServiceProcessor {

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
        PROCESSOR, "NEW_ADDRESS_REPORTED",
        ORIGINAL_CASE_ID, String.valueOf(outcome.getCaseId()));

    boolean isDelivered = isDelivered(outcome);
//    cacheData(outcome, caseId, isDelivered);

    String eventDateTime = dateFormat.format(outcome.getEventDate());

    Map<String, Object> root = new HashMap<>();
    root.put("sourceCase", "NEW_STANDALONE");
    root.put("outcome", outcome);

    // TODO : Fix this shit
    if (outcome.getCeDetails() != null) {
      if (outcome.getCeDetails().getEstablishmentType() == null){
        outcome.getCeDetails().setEstablishmentType("OTHER");
      }
      if (outcome.getCeDetails().getEstablishmentName() == null){
        outcome.getCeDetails().setEstablishmentName(null);
      }
      if (outcome.getCeDetails().getEstablishmentSecure() == null ) {
        outcome.getCeDetails().setEstablishmentSecure("false");
      }
      root.put("ceDetails", outcome.getCeDetails());
      root.put("usualResidents",
          outcome.getCeDetails().getUsualResidents() != null ? outcome.getCeDetails().getUsualResidents() : 0);
    }

    root.put("newCaseId", caseId);
    root.put("address", outcome.getAddress());
    root.put("officerId", outcome.getOfficerId());
    root.put("eventDate", eventDateTime);
    root.put("surveyType", type);
    root.put("region", regionLookup(outcome.getOfficerId()));
    switch (type) {
      case "CE":
        root.put("addressLevel", "E");
        break;
      case "SPG":
      case "HH":
        root.put("addressLevel", "U");
        break;
      default:
        throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, type, "Invalid survey type");
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

//  private void cacheData(OutcomeSuperSetDto outcome, UUID caseId, boolean isDelivered) throws GatewayException {
//    GatewayCache cache = gatewayCacheService.getById(String.valueOf(caseId));
//    if (cache != null) {
//      throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, "Case already exists in cache: {}", caseId);
//    }
//
//    gatewayCacheService.save(GatewayCache.builder()
//        .caseId(String.valueOf(caseId))
//        .delivered(isDelivered)
//        .existsInFwmt(false)
//        .accessInfo(outcome.getAccessInfo())
//        .careCodes(OutcomeSuperSetDto.careCodesToText(outcome.getCareCodes()))
//        .build());
//  }
}
