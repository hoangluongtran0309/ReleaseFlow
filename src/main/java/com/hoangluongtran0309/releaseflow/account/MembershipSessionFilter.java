package com.hoangluongtran0309.releaseflow.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Refuses a session whose member changed since it began (ADR-0043). A session holds a
 * snapshot of the member taken at sign-in; on every authenticated request this reads the
 * member's {@code session_version} and {@code removed_at} — one indexed read — and, when the
 * member was removed or the version moved, ends that session. The session is the only way
 * to act as a member, so this sees every request a changed member can make, the API's
 * included.
 *
 * <p>Not a Spring bean on purpose: a filter bean would also be registered as a servlet
 * filter outside Spring Security, where no security context exists yet.
 */
public final class MembershipSessionFilter extends OncePerRequestFilter {

    static final String ENDED_PATH = "/login?ended";

    private final AppUserRepository appUserRepository;

    public MembershipSessionFilter(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ReleaseFlowPrincipal principal
                && !current(principal)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            // The API answers as for any request without a session; a page says why.
            if (!request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                response.sendRedirect(request.getContextPath() + ENDED_PATH);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean current(ReleaseFlowPrincipal principal) {
        Optional<AppUserRepository.MembershipState> state =
                appUserRepository.findMembershipState(principal.userId(), principal.organizationId());
        return state.isPresent()
                && state.get().getRemovedAt() == null
                && state.get().getSessionVersion() == principal.sessionVersion();
    }
}
