package com.st6.wc.dispute.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.st6.wc.action.AllowedAction;
import com.st6.wc.dispute.AlignmentDispute;
import com.st6.wc.dispute.dto.AlignmentDisputeDto;
import com.st6.wc.enums.DisputeStatus;
import com.st6.wc.enums.FlagType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit proof of {@link DisputeMapper} (task 5.3 base + 5.5b context-aware affordances, Appendix B.8
 * / §15 / §24). The context-free {@link DisputeMapper#toDto(AlignmentDispute)} emits an empty
 * {@code allowedActions} (write-response path — the UI re-reads the plan); the context-aware
 * overload computes the per-viewer dispute affordances that MIRROR (not share) the E18/E19
 * enforcement: {@code RESPOND_DISPUTE} iff the viewer is the owning IC ∧ the dispute is {@code
 * OPEN}; {@code RESOLVE_DISPUTE} iff the viewer is the active direct manager ∧ the dispute is
 * {@code OPEN}|{@code IC_RESPONDED} (§24 "no affordance without enforcement").
 */
class DisputeMapperTest {

  private final DisputeMapper mapper = new DisputeMapper();

  private static final UUID OWNER_IC = UUID.fromString("a0000000-0000-0000-0000-000000000011");
  private static final UUID OTHER = UUID.fromString("a0000000-0000-0000-0000-0000000000ff");

  private static AlignmentDispute dispute(DisputeStatus status) {
    AlignmentDispute d = new AlignmentDispute();
    d.setId(UUID.randomUUID());
    d.setCommitmentId(UUID.randomUUID());
    d.setManagerEmployeeId(UUID.randomUUID());
    d.setStatus(status);
    d.setFlagType(FlagType.MISALIGNED);
    d.setManagerNote("note");
    d.setVersion(0L);
    return d;
  }

  // --- RESPOND_DISPUTE: owning IC viewing an OPEN dispute (mirrors E18 owner ∧ OPEN) ----
  @Test
  void toDto_ownerIc_openDispute_emitsRespondDispute() {
    AlignmentDisputeDto dto = mapper.toDto(dispute(DisputeStatus.OPEN), OWNER_IC, OWNER_IC, false);
    assertThat(dto.allowedActions()).containsExactly(AllowedAction.RESPOND_DISPUTE);
  }

  // --- respond is OPEN-only: the owning IC on an IC_RESPONDED dispute gets NO RESPOND ----
  @Test
  void toDto_ownerIc_icResponded_noRespond() {
    AlignmentDisputeDto dto =
        mapper.toDto(dispute(DisputeStatus.IC_RESPONDED), OWNER_IC, OWNER_IC, false);
    assertThat(dto.allowedActions()).doesNotContain(AllowedAction.RESPOND_DISPUTE);
  }

  // --- RESOLVE_DISPUTE: the direct manager on an OPEN or IC_RESPONDED dispute (mirrors E19) ----
  @Test
  void toDto_manager_openOrIcResponded_emitsResolveDispute() {
    AlignmentDisputeDto onOpen = mapper.toDto(dispute(DisputeStatus.OPEN), OTHER, OWNER_IC, true);
    assertThat(onOpen.allowedActions()).containsExactly(AllowedAction.RESOLVE_DISPUTE);

    AlignmentDisputeDto onResponded =
        mapper.toDto(dispute(DisputeStatus.IC_RESPONDED), OTHER, OWNER_IC, true);
    assertThat(onResponded.allowedActions()).containsExactly(AllowedAction.RESOLVE_DISPUTE);
  }

  // --- a RESOLVED dispute gets no RESOLVE (defensive — a resolved dispute is never nested) ----
  @Test
  void toDto_manager_resolved_noResolve() {
    AlignmentDisputeDto dto = mapper.toDto(dispute(DisputeStatus.RESOLVED), OTHER, OWNER_IC, true);
    assertThat(dto.allowedActions()).doesNotContain(AllowedAction.RESOLVE_DISPUTE);
  }

  // --- the IC viewer (not the manager) gets NO RESOLVE ----
  @Test
  void toDto_icViewer_noResolve() {
    AlignmentDisputeDto dto = mapper.toDto(dispute(DisputeStatus.OPEN), OWNER_IC, OWNER_IC, false);
    assertThat(dto.allowedActions()).doesNotContain(AllowedAction.RESOLVE_DISPUTE);
  }

  // --- the manager viewer (not the owner) gets NO RESPOND ----
  @Test
  void toDto_managerViewer_noRespond() {
    AlignmentDisputeDto dto = mapper.toDto(dispute(DisputeStatus.OPEN), OTHER, OWNER_IC, true);
    assertThat(dto.allowedActions()).doesNotContain(AllowedAction.RESPOND_DISPUTE);
  }

  // --- the no-arg (write-response) overload stays empty ----
  @Test
  void toDto_noArg_emptyActions() {
    assertThat(mapper.toDto(dispute(DisputeStatus.OPEN)).allowedActions()).isEmpty();
  }
}
