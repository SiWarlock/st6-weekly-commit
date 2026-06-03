package com.st6.wc.comment.repo;

import com.st6.wc.comment.Comment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bare Spring Data repository for {@link Comment} (task 1.5). Finder queries land in 1.6. */
public interface CommentRepository extends JpaRepository<Comment, UUID> {}
