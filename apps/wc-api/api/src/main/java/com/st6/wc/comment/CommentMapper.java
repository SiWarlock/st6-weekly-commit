package com.st6.wc.comment;

import com.st6.wc.comment.dto.CommentDto;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link Comment} entity to its B.9 {@link CommentDto} (comments E20/E21). Repo-free (§35) —
 * {@code authorDisplayName} is resolved + batch-loaded by {@link CommentService} and injected here,
 * so the mapper never triggers an N+1. Never returns the entity across the boundary
 * (forbidden-pattern #3).
 */
@Component
public class CommentMapper {

  public CommentDto toDto(Comment comment, String authorDisplayName) {
    return new CommentDto(
        comment.getId(),
        comment.getTargetType(),
        comment.getTargetId(),
        comment.getAuthorEmployeeId(),
        authorDisplayName,
        comment.getParentCommentId(),
        comment.getDepth(),
        comment.getBody(),
        comment.getCreatedAt());
  }
}
