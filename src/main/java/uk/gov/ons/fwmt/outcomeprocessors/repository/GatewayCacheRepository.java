package uk.gov.ons.fwmt.outcomeprocessors.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import uk.gov.ons.fwmt.outcomeprocessors.data.GatewayCache;

import javax.persistence.LockModeType;

@Repository
public interface GatewayCacheRepository extends JpaRepository<GatewayCache, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  GatewayCache findByCaseId(String caseId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  GatewayCache findByOriginalCaseId(String caseId);

}
