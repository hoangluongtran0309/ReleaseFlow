package com.hoangluongtran0309.releaseflow.account;

import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Changing a member's role and removing a member (ADR-0043). Each runs in one transaction
 * that first locks the Organization's row, so the changes of one Organization run one after
 * another and the count of administrators that follows cannot be stale. The database's
 * {@code app_users_keep_an_administrator} trigger refuses the same thing at commit, for any
 * writer; its refusal is answered like the service's own.
 */
@Service
public class MemberService {

    static final int HISTORY_LIMIT = 100;
    private static final String KEEP_AN_ADMINISTRATOR = "app_users_keep_an_administrator";

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationInvitationRepository invitationRepository;
    private final MembershipChangeRepository changeRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    MemberService(
            AppUserRepository appUserRepository,
            OrganizationRepository organizationRepository,
            OrganizationInvitationRepository invitationRepository,
            MembershipChangeRepository changeRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.organizationRepository = organizationRepository;
        this.invitationRepository = invitationRepository;
        this.changeRepository = changeRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Gives a member a new role. The same role changes and records nothing. Demoting an
     * administrator also revokes the invitations they made that are still pending.
     */
    public MemberView changeRole(ReleaseFlowPrincipal administrator, UUID memberId, AppUserRole role) {
        requireAdmin(administrator);
        return guarded(() -> {
            Instant now = now();
            lockOrganization(administrator);
            AppUser member = find(administrator, memberId);
            AppUserRole previous = member.getRole();
            if (previous == role) {
                return view(member);
            }
            if (previous == AppUserRole.ADMIN) {
                requireAnotherAdministrator(administrator);
            }
            member.changeRole(role);
            int revoked = previous == AppUserRole.ADMIN ? revokePendingInvitations(member, now) : 0;
            appUserRepository.saveAndFlush(member);
            changeRepository.save(MembershipChange.roleChanged(member, previous, revoked, administrator, now));
            return view(member);
        });
    }

    /**
     * Ends a membership. The row stays, with its name and email, so every record that names
     * the person still does; its pending invitations are revoked and its sessions stop.
     */
    public void remove(ReleaseFlowPrincipal administrator, UUID memberId) {
        requireAdmin(administrator);
        guarded(() -> {
            Instant now = now();
            lockOrganization(administrator);
            AppUser member = find(administrator, memberId);
            if (member.getRole() == AppUserRole.ADMIN) {
                requireAnotherAdministrator(administrator);
            }
            int revoked = revokePendingInvitations(member, now);
            MembershipChange change = MembershipChange.removed(member, revoked, administrator, now);
            member.remove(administrator.userId(), now);
            appUserRepository.saveAndFlush(member);
            changeRepository.save(change);
            return null;
        });
    }

    /** The Organization's membership changes, newest first. */
    @Transactional(readOnly = true)
    public List<MembershipChangeView> changes(ReleaseFlowPrincipal administrator) {
        requireAdmin(administrator);
        Map<UUID, AppUser> people = everyone(administrator);
        return changeRepository
                .findAllByOrganizationIdOrderByChangedAtDescIdDesc(administrator.organizationId(), Limit.of(HISTORY_LIMIT))
                .stream()
                .map(change -> {
                    AppUser member = people.get(change.getMemberId());
                    return new MembershipChangeView(
                            change.getId(),
                            change.getMemberId(),
                            member.getDisplayName(),
                            member.getEmail(),
                            change.getKind().name(),
                            change.getPreviousRole(),
                            change.getNewRole(),
                            change.getRevokedInvitations(),
                            change.getChangedBy(),
                            change.getChangerName(),
                            change.getChangedAt()
                    );
                })
                .toList();
    }

    /** The people removed from the Organization, most recently removed first. */
    @Transactional(readOnly = true)
    public List<RemovedMemberView> removed(ReleaseFlowPrincipal administrator) {
        requireAdmin(administrator);
        List<AppUser> removed = appUserRepository
                .findAllByOrganizationIdAndRemovedAtIsNotNullOrderByRemovedAtDescIdDesc(administrator.organizationId());
        Map<UUID, AppUser> people = everyone(administrator);
        return removed.stream()
                .map(member -> new RemovedMemberView(
                        member.getId(),
                        member.getDisplayName(),
                        member.getEmail(),
                        member.getRemovedAt(),
                        // The remover's row stays even if they were removed later too.
                        people.get(member.getRemovedBy()).getDisplayName()
                ))
                .toList();
    }

    // Every row of the Organization, removed members included: their names still resolve.
    private Map<UUID, AppUser> everyone(ReleaseFlowPrincipal administrator) {
        return appUserRepository.findAllByOrganizationIdOrderByCreatedAtAscIdAsc(administrator.organizationId())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    private <T> T guarded(Supplier<T> work) {
        try {
            return transactionTemplate.execute(status -> work.get());
        } catch (RuntimeException exception) {
            // The trigger fires at commit, whatever wrote the change.
            if (refusedByKeepAnAdministrator(exception)) {
                throw new LastAdministratorException();
            }
            throw exception;
        }
    }

    private void lockOrganization(ReleaseFlowPrincipal administrator) {
        organizationRepository.lockById(administrator.organizationId()).orElseThrow(MemberNotFoundException::new);
    }

    // Counted after the Organization's lock, so another change cannot be under way.
    private void requireAnotherAdministrator(ReleaseFlowPrincipal administrator) {
        long administrators = appUserRepository.countByOrganizationIdAndRoleAndRemovedAtIsNull(
                administrator.organizationId(), AppUserRole.ADMIN);
        if (administrators <= 1) {
            throw new LastAdministratorException();
        }
    }

    private AppUser find(ReleaseFlowPrincipal administrator, UUID memberId) {
        return appUserRepository.findByIdAndOrganizationId(memberId, administrator.organizationId())
                .filter(member -> !member.isRemoved())
                .orElseThrow(MemberNotFoundException::new);
    }

    // Only administrators invite, so an offer the person made goes with the power to make it.
    private int revokePendingInvitations(AppUser member, Instant now) {
        int revoked = 0;
        for (OrganizationInvitation invitation : invitationRepository.findAllByOrganizationIdAndCreatedByAndStatus(
                member.getOrganizationId(), member.getId(), InvitationStatus.PENDING)) {
            if (invitation.isUsable(now)) {
                invitation.revoke(now);
                revoked++;
            } else {
                invitation.expire(now);
            }
        }
        return revoked;
    }

    private static MemberView view(AppUser member) {
        return new MemberView(member.getId(), member.getEmail(), member.getDisplayName(), member.getRole(),
                member.getCreatedAt());
    }

    private static boolean refusedByKeepAnAdministrator(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof LastAdministratorException) {
                return false;
            }
            if (cause.getMessage() != null && cause.getMessage().contains(KEEP_AN_ADMINISTRATOR)) {
                return true;
            }
        }
        return false;
    }

    // PostgreSQL keeps microseconds.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // Defense in depth: the URL rules already restrict these operations to administrators.
    private static void requireAdmin(ReleaseFlowPrincipal principal) {
        if (principal.role() != AppUserRole.ADMIN) {
            throw new AccessDeniedException("Administrator role required.");
        }
    }
}
