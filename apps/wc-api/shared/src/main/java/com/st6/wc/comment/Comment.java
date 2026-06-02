package com.st6.wc.comment;

import com.st6.wc.common.AbstractAuditingEntity;
import com.st6.wc.enums.CommentTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Comment — flat in MVP, nestable schema (delta 3; Appendix A / §4 / §11), maps {@code comment}.
 * {@code targetType} is the narrowed 2-value {@code {PLAN,COMMITMENT}} vocabulary; {@code
 * parentCommentId}/{@code path} are nullable and {@code depth} defaults 0 (flat). Audited, not
 * versioned (inline {@code @Id} + {@link AbstractAuditingEntity}).
 */
@Entity
@Table(name = "comment")
@Getter
@Setter
public class Comment extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CommentTargetType targetType;

  @Column(nullable = false)
  private UUID targetId;

  @Column(nullable = false)
  private UUID authorEmployeeId;

  private UUID parentCommentId;

  private String path;

  @Column(nullable = false)
  private int depth;

  @Column(nullable = false)
  private String body;
}
