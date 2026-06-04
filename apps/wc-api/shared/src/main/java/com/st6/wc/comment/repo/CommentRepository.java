package com.st6.wc.comment.repo;

import com.st6.wc.comment.Comment;
import com.st6.wc.enums.CommentTargetType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Comment}. The E20 thread finder returns one target's comments
 * paginated; the service passes the server-fixed {@code createdAt ASC, id ASC} {@code Sort} in the
 * {@code Pageable} (deterministic chronological order — §11/F.5). Backed by {@code
 * idx_comment_target (target_type, target_id, path)}.
 */
public interface CommentRepository extends JpaRepository<Comment, UUID> {

  Page<Comment> findByTargetTypeAndTargetId(
      CommentTargetType targetType, UUID targetId, Pageable pageable);
}
