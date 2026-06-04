package com.st6.wc.comment.dto;

import com.st6.wc.enums.CommentTargetType;
import java.time.Instant;
import java.util.UUID;

/**
 * B.9 — the flat comment response DTO (E20/E21, §11). A {@code record} mirroring B.9 verbatim,
 * never the {@link com.st6.wc.comment.Comment} entity across the boundary (forbidden-pattern #3 —
 * audit-quartet / {@code path} leak-tested). {@code parentCommentId} is always {@code null} and
 * {@code depth} always {@code 0} in MVP (the schema is nestable; the UI renders flat). {@code
 * authorDisplayName} is resolved from {@code Employee} by the service (NOT stored on the entity);
 * {@code body} is the RAW user text (React-escapes, §16). {@code createdAt} drives the
 * chronological thread order and is populated explicitly via the service {@code Clock} (the entity
 * {@code @CreatedDate} is a no-op — JPA auditing is unwired).
 */
public record CommentDto(
    UUID id,
    CommentTargetType targetType,
    UUID targetId,
    UUID authorEmployeeId,
    String authorDisplayName,
    UUID parentCommentId,
    int depth,
    String body,
    Instant createdAt) {}
