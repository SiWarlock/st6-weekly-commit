package com.st6.wc.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.st6.wc.audit.AuditService;
import com.st6.wc.auth.DomainAuthorizationService;
import com.st6.wc.auth.ResourceNotFoundOrUnauthorizedException;
import com.st6.wc.comment.dto.CommentDto;
import com.st6.wc.comment.dto.CreateCommentRequest;
import com.st6.wc.comment.repo.CommentRepository;
import com.st6.wc.commitment.repo.WeeklyCommitmentRepository;
import com.st6.wc.employee.Employee;
import com.st6.wc.employee.repo.EmployeeRepository;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.enums.PlanState;
import com.st6.wc.enums.RoleType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.PageEnvelope;
import com.st6.wc.plan.WeeklyPlan;
import com.st6.wc.plan.repo.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * {@link CommentService} unit proof (comments E20/E21, §11 / §6 rule #3 / §15). Pins the
 * mock-verifiable mechanics: the {@code authorizeCommentTargetAccess} chokepoint runs FIRST (repo
 * never touched on a denial, §25); {@code createdAt} is stamped from the injected {@code Clock}
 * (the entity's {@code @CreatedDate} is a no-op — JPA auditing is unwired); and {@code
 * authorDisplayName} is batch-resolved in a SINGLE lookup (no N+1, §35/§37). The DB-level behaviors
 * (ordering, validation, IDOR 404s, the REQ-F-014 manager gate) are proven in {@link
 * CommentEndpointTest}.
 */
class CommentServiceTest {

  private final DomainAuthorizationService authz = mock(DomainAuthorizationService.class);
  private final CommentRepository comments = mock(CommentRepository.class);
  private final EmployeeRepository employees = mock(EmployeeRepository.class);
  private final WeeklyPlanRepository plans = mock(WeeklyPlanRepository.class);
  private final WeeklyCommitmentRepository commitments = mock(WeeklyCommitmentRepository.class);
  private final CommentMapper commentMapper = new CommentMapper(); // pure mapper — use the real one
  private final AuditService auditService = mock(AuditService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-04T12:00:00Z"), ZoneOffset.UTC);

  private final CommentService service =
      new CommentService(
          authz, comments, employees, plans, commitments, commentMapper, auditService, clock);

  private static UserPrincipal ic(UUID id) {
    return new UserPrincipal(id, RoleType.IC, false);
  }

  private static Employee employee(UUID id, String name) {
    Employee e = new Employee();
    e.setId(id);
    e.setEmail(name + "@x.test");
    e.setDisplayName(name);
    e.setRole(RoleType.IC);
    e.setActive(true);
    return e;
  }

  private static WeeklyPlan plan(UUID ownerId, PlanState state) {
    WeeklyPlan p = new WeeklyPlan();
    p.setId(UUID.randomUUID());
    p.setEmployeeId(ownerId);
    p.setState(state);
    return p;
  }

  private static Comment comment(UUID targetId, UUID authorId) {
    Comment c = new Comment();
    c.setId(UUID.randomUUID());
    c.setTargetType(CommentTargetType.PLAN);
    c.setTargetId(targetId);
    c.setAuthorEmployeeId(authorId);
    c.setDepth(0);
    c.setBody("hi");
    c.setCreatedAt(Instant.parse("2026-06-04T11:00:00Z"));
    return c;
  }

  // --- RED #2: authorDisplayName batch-resolved in ONE lookup (no per-row findById N+1) ----------
  @Test
  void list_resolvesAuthorDisplayName_batchLoaded_noNPlusOne() {
    UUID planId = UUID.randomUUID();
    UUID authorA = UUID.randomUUID();
    UUID authorB = UUID.randomUUID();
    UserPrincipal actor = ic(authorA);
    // the actor owns the target plan → the REQ-F-014 gate skips (owner comments any state).
    when(plans.findById(planId)).thenReturn(Optional.of(plan(authorA, PlanState.LOCKED)));
    // 3 comments by 2 distinct authors → the batch must be ONE findAllById of {A,B}, never per-row.
    List<Comment> rows =
        List.of(comment(planId, authorA), comment(planId, authorB), comment(planId, authorA));
    when(comments.findByTargetTypeAndTargetId(
            any(CommentTargetType.class), any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 25), rows.size()));
    when(employees.findAllById(any()))
        .thenReturn(List.of(employee(authorA, "Alice"), employee(authorB, "Bob")));

    PageEnvelope<CommentDto> result =
        service.list(actor, CommentTargetType.PLAN, planId, PageRequest.of(0, 25));

    verify(employees, times(1)).findAllById(any()); // exactly one batch lookup
    verify(employees, never()).findById(any()); // never per-row
    assertThat(result.content())
        .extracting(CommentDto::authorDisplayName)
        .containsExactly("Alice", "Bob", "Alice");
  }

  // --- RED #4: createdAt stamped from the injected Clock (the @CreatedDate is a no-op here)
  // -------
  @Test
  void create_setsCreatedAtViaClock() {
    UUID planId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UserPrincipal owner = ic(ownerId);
    when(plans.findById(planId)).thenReturn(Optional.of(plan(ownerId, PlanState.LOCKED)));
    when(employees.findById(ownerId)).thenReturn(Optional.of(employee(ownerId, "Alice")));
    when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

    CommentDto dto =
        service.create(
            owner, new CreateCommentRequest(CommentTargetType.PLAN, planId, "nice work"));

    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(comments).save(captor.capture());
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(clock.instant());
    assertThat(dto.createdAt()).isEqualTo(clock.instant());
    assertThat(dto.depth()).isZero();
    assertThat(dto.parentCommentId()).isNull();
  }

  // --- RED #9: the authorizer is the chokepoint — a denial never reaches the repo (§25) ----------
  @Test
  void create_authorizerIsChokepoint_repoNeverHitOnDenial() {
    UUID planId = UUID.randomUUID();
    UserPrincipal actor = ic(UUID.randomUUID());
    doThrow(new ResourceNotFoundOrUnauthorizedException())
        .when(authz)
        .authorizeCommentTargetAccess(any(), any(), any());

    assertThatThrownBy(
            () ->
                service.create(
                    actor, new CreateCommentRequest(CommentTargetType.PLAN, planId, "blocked")))
        .isInstanceOf(ResourceNotFoundOrUnauthorizedException.class);

    verify(comments, never()).save(any());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }
}
