package uk.gov.ons.fwmt.outcomeprocessors.converter.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.common.dto.OutcomeSuperSetDto;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.common.rm.dto.ActionInstructionType;
import uk.gov.ons.census.fwmt.common.rm.dto.FwmtActionInstruction;
import uk.gov.ons.census.fwmt.common.service.OutcomeServiceProcessor;
import uk.gov.ons.census.fwmt.events.component.GatewayEventManager;
import uk.gov.ons.fwmt.outcomeprocessors.data.GatewayCache;
import uk.gov.ons.fwmt.outcomeprocessors.message.RmFieldRepublishProducer;
import uk.gov.ons.fwmt.outcomeprocessors.service.GatewayCacheService;

import java.util.UUID;

import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.*;

@Slf4j
@Component("DELIVERED_FEEDBACK")
public class DeliveredFeedbackProcessor implements OutcomeServiceProcessor {

  @Autowired
  private RmFieldRepublishProducer rmFieldRepublishProducer;

  @Autowired
  private GatewayEventManager gatewayEventManager;

  @Autowired
  private GatewayCacheService gatewayCacheService;

  @Override
  public UUID process(OutcomeSuperSetDto outcome, UUID caseIdHolder, String type) throws GatewayException {
    UUID caseId = (caseIdHolder != null) ? caseIdHolder : outcome.getCaseId();

    gatewayEventManager.triggerEvent(String.valueOf(caseId), PROCESSING_OUTCOME,
        SURVEY_TYPE, type,
        PROCESSOR, "DELIVERED_FEEDBACK",
        ORIGINAL_CASE_ID, String.valueOf(outcome.getCaseId()),
        SITE_CASE_ID, (outcome.getSiteCaseId() != null ? String.valueOf(outcome.getSiteCaseId()) : "N/A"));

    GatewayCache cache = gatewayCacheService.getById(String.valueOf(caseId));

    if (cache != null && ("CANCEL".equals(cache.lastActionInstruction) || "CANCEL(HELD)".equals(cache.lastActionInstruction))) {
      gatewayCacheService.save(cache.toBuilder()
          .lastActionInstruction(ActionInstructionType.UPDATE.toString())
          .build());
    }

    FwmtActionInstruction fieldworkFollowup = FwmtActionInstruction.builder()
        .actionInstruction(ActionInstructionType.UPDATE)
        .surveyName("CENSUS")
        .addressType(type)
        .addressLevel("U")
        .caseId(caseId.toString())
        .build();

    rmFieldRepublishProducer.republish(fieldworkFollowup);

    gatewayEventManager.triggerEvent(String.valueOf(caseId), RM_FIELD_REPUBLISH,
        SURVEY_NAME, "CENSUS",
        ADDRESS_TYPE, type,
        ACTION_INSTRUCTION_TYPE, ActionInstructionType.UPDATE.toString(),
        TRANSACTION_ID, outcome.getTransactionId().toString());

    return caseId;
  }
}
