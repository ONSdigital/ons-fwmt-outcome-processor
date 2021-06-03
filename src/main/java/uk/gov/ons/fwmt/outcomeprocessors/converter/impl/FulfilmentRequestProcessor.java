package uk.gov.ons.fwmt.outcomeprocessors.converter.impl;

import static uk.gov.ons.ctp.integration.common.product.model.Product.RequestChannel.FIELD;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.ORIGINAL_CASE_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.OUTCOME_SENT;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.PROCESSING_OUTCOME;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.PROCESSOR;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.ROUTING_KEY;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.SITE_CASE_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.SURVEY_TYPE;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.TEMPLATE_TYPE;
import static uk.gov.ons.fwmt.outcomeprocessors.converter.OutcomeServiceLogConfig.TRANSACTION_ID;
import static uk.gov.ons.fwmt.outcomeprocessors.enums.EventType.FULFILMENT_REQUESTED;

import java.text.DateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.common.dto.FulfilmentRequestDto;
import uk.gov.ons.census.fwmt.common.dto.OutcomeSuperSetDto;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.common.service.OutcomeServiceProcessor;
import uk.gov.ons.census.fwmt.events.component.GatewayEventManager;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.integration.common.product.ProductReference;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.fwmt.outcomeprocessors.config.GatewayOutcomeQueueConfig;
import uk.gov.ons.fwmt.outcomeprocessors.message.GatewayOutcomeProducer;
import uk.gov.ons.fwmt.outcomeprocessors.template.TemplateCreator;

@Slf4j
@Component("FULFILMENT_REQUESTED")
public class FulfilmentRequestProcessor implements OutcomeServiceProcessor {

  @Autowired
  private DateFormat dateFormat;

  @Autowired
  private ProductReference productReference;

  @Autowired
  private GatewayOutcomeProducer gatewayOutcomeProducer;

  @Autowired
  private GatewayEventManager gatewayEventManager;

//  @Autowired
//  private GatewayCacheService gatewayCacheService;

  @Override
  public UUID process(OutcomeSuperSetDto outcome, UUID caseIdHolder, String type) throws GatewayException {
    gatewayEventManager.triggerEvent(String.valueOf(outcome.getCaseId()), PROCESSING_OUTCOME,
        SURVEY_TYPE, type,
        PROCESSOR, "FULFILMENT_REQUESTED",
        ORIGINAL_CASE_ID, String.valueOf(outcome.getCaseId()),
        SITE_CASE_ID, (outcome.getSiteCaseId() != null ? String.valueOf(outcome.getSiteCaseId()) : "N/A"));
 
    if (outcome.getFulfilmentRequests() == null) return caseIdHolder;
    UUID caseId = (caseIdHolder != null) ? caseIdHolder : outcome.getCaseId();
    for (FulfilmentRequestDto fulfilmentRequest : outcome.getFulfilmentRequests()) {
      if (!isQuestionnaireLinked(fulfilmentRequest) && fulfilmentRequest.getQuestionnaireType() != null) {
        String eventDateTime = dateFormat.format(outcome.getEventDate());
        Map<String, Object> root = new HashMap<>();
        root.put("outcome", outcome);
        root.put("caseId", caseId);
        root.put("eventDate", eventDateTime);
        String outcomeEvent = createQuestionnaireRequiredByPostEvent(root, fulfilmentRequest, String.valueOf(caseId),
            type);

        gatewayOutcomeProducer.sendOutcome(outcomeEvent, String.valueOf(outcome.getTransactionId()),
            GatewayOutcomeQueueConfig.GATEWAY_FULFILMENT_REQUEST_ROUTING_KEY);

        gatewayEventManager.triggerEvent(String.valueOf(caseIdHolder), OUTCOME_SENT,
            SURVEY_TYPE, type,
            TEMPLATE_TYPE, FULFILMENT_REQUESTED.toString(),
            TRANSACTION_ID, outcome.getTransactionId().toString(),
            ROUTING_KEY, GatewayOutcomeQueueConfig.GATEWAY_FULFILMENT_REQUEST_ROUTING_KEY);
      }
    }
    return caseId;
  }

  private String createQuestionnaireRequiredByPostEvent(Map<String, Object> root,
    FulfilmentRequestDto fulfilmentRequest, String caseId, String type) throws GatewayException {
    String individualCaseId = "";

    List<Product> productList = getProductFromQuestionnaireType(fulfilmentRequest);
    if (productList.get(0).getIndividual() && type.equals("HH")) {
      individualCaseId = String.valueOf(UUID.randomUUID());
      root.put("individualCaseId", individualCaseId);
      root.put("surveyType", type);
    }
    root.put("packcode", productList.get(0).getFulfilmentCode());
    root.put("requesterTitle", fulfilmentRequest.getRequesterTitle());
    root.put("requesterForename", fulfilmentRequest.getRequesterForename());
    root.put("requesterSurname", fulfilmentRequest.getRequesterSurname());
    root.put("requesterPhone", fulfilmentRequest.getRequesterPhone());

//    cacheData(caseId, individualCaseId);

    return TemplateCreator.createOutcomeMessage(FULFILMENT_REQUESTED, root);
  }

  private List<Product> getProductFromQuestionnaireType(FulfilmentRequestDto fulfilmentRequest) {
    Product product = new Product();
    List<Product.RequestChannel> requestChannels = Collections.singletonList(FIELD);

    product.setRequestChannels(requestChannels);
    product.setFulfilmentCode(fulfilmentRequest.getQuestionnaireType());

    List<Product> productList = null;
    try {
      productList = productReference.searchProducts(product);
      if (productList.size() != 1) throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR,
          "Failed to find 1 product using Product code: " + fulfilmentRequest.getQuestionnaireType());
    } catch (CTPException | GatewayException e) {
      log.error("Error within Product Lookup {}", e);
    }
    return productList;
  }

  private boolean isQuestionnaireLinked(FulfilmentRequestDto fulfilmentRequest) {
    return (fulfilmentRequest.getQuestionnaireID() != null);
  }

//  private void cacheData(String caseId, String individualCaseId) {
//    GatewayCache cache = gatewayCacheService.getById(caseId);
//    GatewayCacheBuilder builder ;
//    if (cache == null) builder = GatewayCache.builder();
//    else builder = cache.toBuilder();
//
//    if (!individualCaseId.equals("")) {
//      gatewayCacheService.save(builder
//          .caseId(caseId)
//          .delivered(true)
//          .individualCaseId(individualCaseId)
//          .build());
//    } else {
//      gatewayCacheService.save(builder
//          .caseId(caseId)
//          .delivered(true)
//          .build());
//    }
//  }
}