package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrganizationMemberApiController {

    private final InvitationService invitationService;
    private final MemberService memberService;

    OrganizationMemberApiController(InvitationService invitationService, MemberService memberService) {
        this.invitationService = invitationService;
        this.memberService = memberService;
    }

    @GetMapping("/members")
    List<MemberView> members(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return invitationService.members(principal);
    }

    // The member's sessions stop on their next request (ADR-0043).
    @PutMapping("/members/{memberId}/role")
    MemberView changeRole(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID memberId,
            @Valid @RequestBody MemberRoleRequest request
    ) {
        return memberService.changeRole(principal, memberId, request.appUserRole());
    }

    // The row stays as the record of who the person was; the membership ends.
    @DeleteMapping("/members/{memberId}")
    ResponseEntity<Void> remove(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID memberId) {
        memberService.remove(principal, memberId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/members/changes")
    List<MembershipChangeView> changes(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return memberService.changes(principal);
    }

    @GetMapping("/invitations")
    List<InvitationView> invitations(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return invitationService.invitations(principal);
    }

    @PostMapping("/invitations")
    ResponseEntity<IssuedInvitation> invite(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody InvitationRequest request
    ) {
        return issued(invitationService.invite(principal, request));
    }

    @PostMapping("/invitations/{invitationId}/reissue")
    ResponseEntity<IssuedInvitation> reissue(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID invitationId
    ) {
        return issued(invitationService.reissue(principal, invitationId));
    }

    @DeleteMapping("/invitations/{invitationId}")
    ResponseEntity<Void> revoke(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID invitationId
    ) {
        invitationService.revoke(principal, invitationId);
        return ResponseEntity.noContent().build();
    }

    // The response carries the only copy of the raw token, so it must never be cached.
    private static ResponseEntity<IssuedInvitation> issued(IssuedInvitation invitation) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(invitation);
    }
}
