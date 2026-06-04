package com.st6.wc.manager.dto;

import com.st6.wc.commitment.dto.WeeklyCommitmentDto;
import java.util.UUID;

/**
 * B.12 — one Supporting-Outcome group in the E15 drill-down: the report's commitments under one SO
 * (within the cell's Defining Objective), as a B.20 paginated envelope ({@code priority ASC,
 * createdAt ASC, id ASC} — the {@code id} tie-break keeps pagination stable while {@code createdAt}
 * is unpopulated). A {@code record}; the commitments are the context-free {@link
 * WeeklyCommitmentDto} (empty {@code allowedActions} — dispute affordances are E3/E4-only, §35).
 */
public record DrilldownOutcomeGroup(
    UUID supportingOutcomeId,
    String supportingOutcomeTitle,
    PageEnvelope<WeeklyCommitmentDto> commitments) {}
