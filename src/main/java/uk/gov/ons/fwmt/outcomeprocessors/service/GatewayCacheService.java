package uk.gov.ons.fwmt.outcomeprocessors.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.ons.fwmt.outcomeprocessors.data.GatewayCache;
import uk.gov.ons.fwmt.outcomeprocessors.repository.GatewayCacheRepository;

/**
 * This class is bare-bones because it's a simple connector between the rest of the code and the caching implementation
 * Please don't subvert this class by touching the GatewayCacheRepository
 * If we ever change from a database to redis, this class will form the breaking point
 */

@Slf4j
@Service
public class GatewayCacheService {

  @Autowired
  private GatewayCacheRepository repository;

  public GatewayCache getById(String caseId) {
    return repository.findByCaseId(caseId);
  }

  public GatewayCache getByOriginalId(String caseId) {
    return repository.findByOriginalCaseId(caseId);
  }

  public void save(GatewayCache cache) {
    repository.save(cache);
  }

}
