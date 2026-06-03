package com.st6.wc.identity;

/**
 * The sealed boundary type for "who is acting" in a domain authorization check (task 2.5). Permits
 * exactly the two principals: {@link UserPrincipal} (an authenticated IC/manager, subject to
 * self/direct-report scoping) and {@link SystemPrincipal} (the no-HTTP SYSTEM actor, exempt from
 * those checks). {@code DomainAuthorizationService} accepts this marker and switches on the two
 * cases; sealing makes that switch exhaustive (2.4 left this door open for 2.5).
 */
public sealed interface DomainPrincipal permits UserPrincipal, SystemPrincipal {}
