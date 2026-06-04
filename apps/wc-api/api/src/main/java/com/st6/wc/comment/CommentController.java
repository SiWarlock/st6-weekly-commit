package com.st6.wc.comment;

import com.st6.wc.comment.dto.CommentDto;
import com.st6.wc.comment.dto.CreateCommentRequest;
import com.st6.wc.enums.CommentTargetType;
import com.st6.wc.identity.UserPrincipal;
import com.st6.wc.manager.dto.PageEnvelope;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comments read+create surface (§5 E20/E21, §11). Thin controller — the rule-#3 IDOR chokepoint,
 * the REQ-F-014 manager gate, validation, and the audit all live in {@link CommentService}
 * (controllers never authorize, forbidden-pattern #4). {@code Pageable} resolves via Spring Data
 * Web (size default 25 / max 100); a mistyped {@code targetType} enum fails binding → {@code 400}.
 */
@RestController
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @GetMapping("/api/comments")
  public PageEnvelope<CommentDto> list(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam("targetType") CommentTargetType targetType,
      @RequestParam("targetId") UUID targetId,
      Pageable pageable) {
    return commentService.list(principal, targetType, targetId, pageable);
  }

  @PostMapping("/api/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public CommentDto create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateCommentRequest request) {
    return commentService.create(principal, request);
  }
}
