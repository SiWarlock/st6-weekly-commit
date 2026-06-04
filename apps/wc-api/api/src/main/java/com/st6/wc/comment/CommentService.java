package com.st6.wc.comment;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.comment.dto.CommentDto;
import com.st6.wc.comment.dto.CreateCommentRequest;
import com.st6.wc.comment.repo.CommentRepository;
import com.st6.wc.commitment.WeeklyCommitment;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.PageEnvelope;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import com.st6.wc.web.IllegalStateTransitionException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comments read+create (E20/E21, §11 / §6 rule #3 / §15/§16 / REQ-F-014). The FIRST production
 * caller of {@link DomainAuthorizationService#authorizeCommentTargetAccess} — the rule-#3 IDOR
 * chokepoint is the FIRST statement on both paths (cross-owner/unseeable/missing target → codeless
 * {@code 404} + denial audit via the authorizer; §25). After access, the <strong>REQ-F-014 manager
 * gate</strong> applies uniformly to read AND write: a <em>non-owner</em> (the active direct
 * manager the authorizer admitted) may comment on a direct-report target <strong>only when the
 * owning plan is {@code LOCKED}+</strong> — a manager on a {@code DRAFT} target gets {@code 409
 * ILLEGAL_STATE_TRANSITION} (no audit; the 5.6 manager-DRAFT posture, mirroring {@link
 * com.st6.wc.review.ReviewService#markReviewed}). The owning IC comments in any state. {@code
 * createdAt} is stamped from the injected {@code Clock} (the entity {@code @CreatedDate} is
 * unwired); {@code authorDisplayName} is batch-resolved (one {@code WHERE id IN}); the create emits
 * a body-free {@code COMMENT_CREATED} audit (§15). The {@code body} is stored RAW (§16) and never
 * logged/audited.
 */
@Service
public class CommentService {

  private static final int MAX_PAGE_SIZE = 100; // B.20 backstop
  // §11/F.5 chronological order + the id tie-break (deterministic while createdAt has
  // second-grain).
  private static final Sort COMMENT_SORT =
      Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

  private final DomainAuthorizationService authz;
  private final CommentRepository comments;
  private final EmployeeRepository employees;
  private final WeeklyPlanRepository plans;
  private final WeeklyCommitmentRepository commitments;
  private final CommentMapper commentMapper;
  private final AuditService auditService;
  private final Clock clock;

  public CommentService(
      DomainAuthorizationService authz,
      CommentRepository comments,
      EmployeeRepository employees,
      WeeklyPlanRepository plans,
      WeeklyCommitmentRepository commitments,
      CommentMapper commentMapper,
      AuditService auditService,
      Clock clock) {
    this.authz = authz;
    this.comments = comments;
    this.employees = employees;
    this.plans = plans;
    this.commitments = commitments;
    this.commentMapper = commentMapper;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageEnvelope<CommentDto> list(
      UserPrincipal actor, CommentTargetType targetType, UUID targetId, Pageable pageable) {
    authz.authorizeCommentTargetAccess(actor, targetType, targetId); // chokepoint FIRST (IDOR 404)
    enforceManagerCommentGate(
        actor, resolveOwningPlan(targetType, targetId)); // REQ-F-014 (read too)

    Pageable effective =
        PageRequest.of(
            pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
            COMMENT_SORT); // server-fixed sort — ignore the client sort for determinism
    Page<Comment> page = comments.findByTargetTypeAndTargetId(targetType, targetId, effective);
    Map<UUID, String> authorNames = batchAuthorNames(page.getContent());
    return PageEnvelope.of(
        page.map(c -> commentMapper.toDto(c, authorNames.get(c.getAuthorEmployeeId()))));
  }

  @Transactional
  public CommentDto create(UserPrincipal actor, CreateCommentRequest req) {
    authz.authorizeCommentTargetAccess(
        actor, req.targetType(), req.targetId()); // chokepoint FIRST (IDOR 404 + denial audit)
    enforceManagerCommentGate(actor, resolveOwningPlan(req.targetType(), req.targetId()));

    Comment comment = new Comment();
    comment.setId(UUID.randomUUID());
    comment.setTargetType(req.targetType());
    comment.setTargetId(req.targetId());
    comment.setAuthorEmployeeId(actor.employeeId());
    comment.setDepth(0); // flat MVP (nestable schema; parentCommentId stays null)
    comment.setBody(req.body()); // normalized at the DTO boundary; stored RAW (§16)
    comment.setCreatedAt(clock.instant()); // explicit — the @CreatedDate is a no-op (auditing off)
    comments.save(comment);

    // §15 — ids-only safe metadata (targetType + targetId), NEVER the body text.
    auditService.record(
        "COMMENT_CREATED",
        "Comment",
        comment.getId(),
        actor.employeeId(),
        "Comment created",
        "{\"targetType\":\"%s\",\"targetId\":\"%s\"}".formatted(req.targetType(), req.targetId()));

    String authorName =
        employees.findById(actor.employeeId()).map(Employee::getDisplayName).orElse(null);
    return commentMapper.toDto(comment, authorName);
  }

  /**
   * REQ-F-014: a non-owner (necessarily the active direct manager — the only non-owner the access
   * chokepoint admits) may comment on a direct-report target only once the owning plan is {@code
   * LOCKED}+. A manager on a {@code DRAFT} target → {@code 409 ILLEGAL_STATE_TRANSITION} (no audit;
   * a legitimate relationship, just a state precondition — the 5.6 posture). The owning IC is
   * exempt.
   */
  private void enforceManagerCommentGate(UserPrincipal actor, WeeklyPlan plan) {
    if (!actor.employeeId().equals(plan.getEmployeeId()) && plan.getState() == PlanState.DRAFT) {
      throw new IllegalStateTransitionException();
    }
  }

  /** Resolve the plan owning a comment target (the {@code COMMITMENT} target hops via its plan). */
  private WeeklyPlan resolveOwningPlan(CommentTargetType targetType, UUID targetId) {
    return switch (targetType) {
      case PLAN ->
          plans.findById(targetId).orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
      case COMMITMENT -> {
        WeeklyCommitment c =
            commitments
                .findById(targetId)
                .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
        yield plans
            .findById(c.getWeeklyPlanId())
            .orElseThrow(ResourceNotFoundOrUnauthorizedException::new);
      }
    };
  }

  /** One batch lookup of the page's distinct authors → {@code authorId → displayName} (no N+1). */
  private Map<UUID, String> batchAuthorNames(List<Comment> rows) {
    Set<UUID> authorIds =
        rows.stream().map(Comment::getAuthorEmployeeId).collect(Collectors.toSet());
    Map<UUID, String> names = new HashMap<>();
    employees.findAllById(authorIds).forEach(e -> names.put(e.getId(), e.getDisplayName()));
    return names;
  }
}
