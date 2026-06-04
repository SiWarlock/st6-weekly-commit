package com.st6.wc.sync;

import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.enums.SyncRelatedType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.sync.dto.OutlookSyncRecordDto;
import com.st6.wc.sync.repo.OutlookCalendarSyncRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E22 — list a plan's Outlook sync records (§10 / §6 rule #3). <strong>Owner-only</strong>: {@link
 * DomainAuthorizationService#authorizeSyncListAccess} is the FIRST statement (the sync list is the
 * IC's private view — a manager-of-owner gets an IDOR-safe codeless {@code 404} + denial audit,
 * unlike the manager-inclusive E23 retry). Returns the plan's {@code WEEKLY_PLAN} records (its
 * {@code IC_PLANNING} + {@code IC_RECONCILIATION}) as a bare array (NOT paginated — matches the
 * frontend), server-ordered {@code eventKind ASC, id ASC}.
 */
@Service
public class SyncReadService {

  private final DomainAuthorizationService authz;
  private final OutlookCalendarSyncRecordRepository syncRecords;
  private final SyncRecordMapper mapper;

  public SyncReadService(
      DomainAuthorizationService authz,
      OutlookCalendarSyncRecordRepository syncRecords,
      SyncRecordMapper mapper) {
    this.authz = authz;
    this.syncRecords = syncRecords;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public List<OutlookSyncRecordDto> listForPlan(UserPrincipal actor, UUID planId) {
    authz.authorizeSyncListAccess(actor, planId); // OWNER-ONLY chokepoint (IDOR 404 + denial audit)
    return syncRecords
        .findByRelatedTypeAndRelatedIdOrderByEventKindAscIdAsc(SyncRelatedType.WEEKLY_PLAN, planId)
        .stream()
        .map(mapper::toDto)
        .toList();
  }
}
