package com.familyfood.access.application;

import com.familyfood.access.HealthAccessFixture;
import com.familyfood.access.domain.AccessGrant;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.infra.AccessGrantRepository;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Making, changing and withdrawing grants (D-044, D-045). Each test is one of the sentences in
 * {@link AccessGrantService}'s contract: only you share your data; a child's data is never shared
 * this way; you share with an adult in your household; changing your mind is immediate and keeps
 * its history.
 */
class AccessGrantServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final String RESOURCE = "HEALTH_ACCESS_GRANT";

    private static final UUID HOME = UUID.randomUUID();
    private static final UUID ELSEWHERE = UUID.randomUUID();
    private static final UUID ME_ACCOUNT = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID SPOUSE_ACCOUNT = UUID.randomUUID();
    private static final UUID SPOUSE = UUID.randomUUID();
    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID OUTSIDER_ACCOUNT = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();

    private HealthAccessFixture fixture;
    private AccessGrantRepository grants;
    private AuditService audit;
    private AccessGrantService service;

    @BeforeEach
    void setUp() {
        fixture = new HealthAccessFixture(NOW);
        grants = fixture.grants;
        audit = fixture.audit;
        service = new AccessGrantService(grants, fixture.households, audit, Clock.fixed(NOW, ZoneOffset.UTC));

        fixture.adult(HOME, ME_ACCOUNT, ME);
        fixture.adult(HOME, SPOUSE_ACCOUNT, SPOUSE);
        fixture.child(HOME, CHILD, ME_ACCOUNT);
        fixture.adult(ELSEWHERE, OUTSIDER_ACCOUNT, OUTSIDER);

        when(grants.save(any(AccessGrant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(grants.saveAndFlush(any(AccessGrant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AppException thrown(Runnable action) {
        try {
            action.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("expected an AppException");
    }

    private static void assertNotAllowed(AppException e, String reason) {
        assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_GRANT_NOT_ALLOWED);
        assertThat(e.details()).containsEntry("reason", reason);
    }

    /** A live grant as the repository would return it for the (subject, audience) lookup. */
    private AccessGrant givenLive(UUID subject, UUID audience, AccessScope scope, AccessLevel level) {
        AccessGrant grant = AccessGrant.grant(subject, audience, scope, level, ME_ACCOUNT, NOW.minusSeconds(3600));
        when(grants.findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(subject, audience))
                .thenReturn(List.of(grant));
        return grant;
    }

    /* ------------------------------------------------------------------- grant */

    @Test
    @DisplayName("the subject grants VIEW on one scope to an adult in their household")
    void subjectGrantsToHouseholdAdult() {
        AccessGrantView view = service.grant(ME_ACCOUNT, ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        assertThat(view.subjectMemberId()).isEqualTo(ME);
        assertThat(view.audienceMemberId()).isEqualTo(SPOUSE);
        assertThat(view.scope()).isEqualTo(AccessScope.ACTIVITY);
        assertThat(view.level()).isEqualTo(AccessLevel.VIEW);
        assertThat(view.subjectLabel()).isEqualTo(fixture.labelOf(ME));
        assertThat(view.audienceLabel()).isEqualTo(fixture.labelOf(SPOUSE));
        assertThat(view.revokedAt()).isNull();

        ArgumentCaptor<AccessGrant> saved = ArgumentCaptor.forClass(AccessGrant.class);
        verify(grants).save(saved.capture());
        assertThat(saved.getValue().getGrantedByUserId()).isEqualTo(ME_ACCOUNT);
        assertThat(saved.getValue().getGrantedAt()).isEqualTo(NOW);
        verify(audit).successForSubject(eq(AuditAction.ACCESS_GRANTED), eq(RESOURCE), eq(saved.getValue().getId()),
                eq(ME_ACCOUNT), eq(HOME), eq(ME), anyMap());
    }

    @Test
    @DisplayName("only the subject may grant — not another adult, not the household owner (D-002)")
    void onlySubjectMayGrant() {
        fixture.role(SPOUSE, "OWNER");

        AppException e = thrown(() -> service.grant(SPOUSE_ACCOUNT, ME, SPOUSE, AccessScope.BODY, AccessLevel.MANAGE));

        assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(audit).deniedForSubject(eq(AuditAction.ACCESS_GRANTED), eq(RESOURCE), eq(ME),
                eq(SPOUSE_ACCOUNT), eq(HOME), eq(ME), anyString());
        verify(grants, never()).save(any());
        verify(grants, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a guardian cannot grant a child's data to anyone — it is reachable only by the guardian link")
    void childDataIsNeverGrantable() {
        AppException e = thrown(() -> service.grant(ME_ACCOUNT, CHILD, SPOUSE, AccessScope.SLEEP, AccessLevel.VIEW));

        assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(grants, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = AccessScope.class, names = {"FOOD_PROFILE", "MEDICATIONS", "APPOINTMENTS", "RECORDS"})
    @DisplayName("a scope whose feature does not exist yet is not offered")
    void unofferedScopeRefused(AccessScope scope) {
        assertNotAllowed(thrown(() -> service.grant(ME_ACCOUNT, ME, SPOUSE, scope, AccessLevel.VIEW)),
                "SCOPE_NOT_OFFERED");
        verify(grants, never()).save(any());
    }

    @Test
    @DisplayName("granting to yourself is refused — you always see your own data")
    void selfGrantRefused() {
        assertNotAllowed(thrown(() -> service.grant(ME_ACCOUNT, ME, ME, AccessScope.ACTIVITY, AccessLevel.VIEW)),
                "SELF_GRANT");
    }

    @Test
    @DisplayName("an unknown or departed audience is refused")
    void unknownOrDepartedAudienceRefused() {
        assertNotAllowed(thrown(() -> service.grant(ME_ACCOUNT, ME, UUID.randomUUID(),
                AccessScope.ACTIVITY, AccessLevel.VIEW)), "AUDIENCE_NOT_FOUND");

        fixture.depart(SPOUSE);
        assertNotAllowed(thrown(() -> service.grant(ME_ACCOUNT, ME, SPOUSE,
                AccessScope.ACTIVITY, AccessLevel.VIEW)), "AUDIENCE_NOT_FOUND");
    }

    @Test
    @DisplayName("granting to someone in another household is refused")
    void otherHouseholdRefused() {
        assertNotAllowed(thrown(() -> service.grant(ME_ACCOUNT, ME, OUTSIDER, AccessScope.ACTIVITY, AccessLevel.VIEW)),
                "NOT_SAME_HOUSEHOLD");
        verify(grants, never()).save(any());
    }

    @Test
    @DisplayName("a child cannot be an audience")
    void childAudienceRefused() {
        assertNotAllowed(thrown(() -> service.grant(ME_ACCOUNT, ME, CHILD, AccessScope.ACTIVITY, AccessLevel.VIEW)),
                "AUDIENCE_NOT_ADULT");
        verify(grants, never()).save(any());
    }

    @Test
    @DisplayName("re-granting at the same level is idempotent — no new row, no new event")
    void sameLevelRegrantIsIdempotent() {
        AccessGrant existing = givenLive(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        AccessGrantView view = service.grant(ME_ACCOUNT, ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        assertThat(view.id()).isEqualTo(existing.getId());
        verify(grants, never()).save(any());
        verify(grants, never()).saveAndFlush(any());
        verify(audit, never()).successForSubject(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("changing the level revokes the old grant (flushed first) and makes a new one")
    void levelChangeRevokesThenRegrants() {
        AccessGrant existing = givenLive(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        AccessGrantView view = service.grant(ME_ACCOUNT, ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.MANAGE);

        assertThat(existing.getRevokedAt()).isEqualTo(NOW);
        assertThat(view.id()).isNotEqualTo(existing.getId());
        assertThat(view.level()).isEqualTo(AccessLevel.MANAGE);

        // The old row must reach the database before the new one, or the one-live-grant index
        // sees two live rows at once.
        InOrder order = inOrder(grants);
        order.verify(grants).saveAndFlush(existing);
        ArgumentCaptor<AccessGrant> created = ArgumentCaptor.forClass(AccessGrant.class);
        order.verify(grants).save(created.capture());
        assertThat(created.getValue().isLive()).isTrue();
        assertThat(created.getValue().getLevel()).isEqualTo(AccessLevel.MANAGE);
    }

    @Test
    @DisplayName("a grant on another scope leaves the existing one alone")
    void otherScopeUnaffected() {
        AccessGrant existing = givenLive(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW);

        service.grant(ME_ACCOUNT, ME, SPOUSE, AccessScope.SLEEP, AccessLevel.VIEW);

        assertThat(existing.isLive()).isTrue();
        verify(grants, never()).saveAndFlush(any());
        verify(grants).save(any(AccessGrant.class));
    }

    /* ------------------------------------------------------------------ revoke */

    @Test
    @DisplayName("the subject revokes a grant: the row is kept, marked revoked, and audited")
    void revokeMarksRevoked() {
        AccessGrant live = AccessGrant.grant(ME, SPOUSE, AccessScope.BODY, AccessLevel.MANAGE, ME_ACCOUNT, NOW.minusSeconds(60));
        when(grants.findByIdAndSubjectMemberId(live.getId(), ME)).thenReturn(Optional.of(live));

        service.revoke(ME_ACCOUNT, ME, live.getId());

        assertThat(live.getRevokedAt()).isEqualTo(NOW);
        verify(grants).save(live);
        verify(grants, never()).delete(any());
        verify(audit).successForSubject(eq(AuditAction.ACCESS_REVOKED), eq(RESOURCE), eq(live.getId()),
                eq(ME_ACCOUNT), eq(HOME), eq(ME), anyMap());
    }

    @Test
    @DisplayName("revoking an already-revoked grant is idempotent and keeps the original end time")
    void revokeIsIdempotent() {
        Instant endedEarlier = NOW.minusSeconds(600);
        AccessGrant revoked = AccessGrant.grant(ME, SPOUSE, AccessScope.BODY, AccessLevel.VIEW, ME_ACCOUNT, NOW.minusSeconds(6000));
        revoked.revoke(endedEarlier);
        when(grants.findByIdAndSubjectMemberId(revoked.getId(), ME)).thenReturn(Optional.of(revoked));

        service.revoke(ME_ACCOUNT, ME, revoked.getId());

        assertThat(revoked.getRevokedAt()).isEqualTo(endedEarlier);
        verify(grants, never()).save(any());
        verify(audit, never()).successForSubject(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("only the subject may revoke — refused and audited")
    void onlySubjectMayRevoke() {
        UUID grantId = UUID.randomUUID();

        AppException e = thrown(() -> service.revoke(SPOUSE_ACCOUNT, ME, grantId));

        assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(audit).deniedForSubject(eq(AuditAction.ACCESS_REVOKED), eq(RESOURCE), eq(ME),
                eq(SPOUSE_ACCOUNT), eq(HOME), eq(ME), anyString());
        verify(grants, never()).findByIdAndSubjectMemberId(any(), any());
    }

    @Test
    @DisplayName("a grant id that is not the subject's own is not found")
    void revokeUnknownGrantIsNotFound() {
        when(grants.findByIdAndSubjectMemberId(any(), eq(ME))).thenReturn(Optional.empty());

        assertThat(thrown(() -> service.revoke(ME_ACCOUNT, ME, UUID.randomUUID())).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /* -------------------------------------------------------------------- lists */

    @Test
    @DisplayName("only the subject may list what they share")
    void onlySubjectMayListOutgoing() {
        AppException e = thrown(() -> service.listOutgoing(SPOUSE_ACCOUNT, ME));

        assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(grants, never()).findBySubjectMemberIdAndRevokedAtIsNullOrderByGrantedAtDesc(any());
    }

    @Test
    @DisplayName("the subject lists their live grants")
    void subjectListsOutgoing() {
        AccessGrant live = AccessGrant.grant(ME, SPOUSE, AccessScope.SLEEP, AccessLevel.VIEW, ME_ACCOUNT, NOW);
        when(grants.findBySubjectMemberIdAndRevokedAtIsNullOrderByGrantedAtDesc(ME)).thenReturn(List.of(live));

        assertThat(service.listOutgoing(ME_ACCOUNT, ME)).extracting(AccessGrantView::id).containsExactly(live.getId());
    }

    @Test
    @DisplayName("'shared with me' leaves out subjects who have left the household")
    void incomingSkipsDepartedSubjects() {
        UUID leaver = UUID.randomUUID();
        fixture.adult(HOME, UUID.randomUUID(), leaver);
        AccessGrant fromMe = AccessGrant.grant(ME, SPOUSE, AccessScope.ACTIVITY, AccessLevel.VIEW, ME_ACCOUNT, NOW);
        AccessGrant fromLeaver = AccessGrant.grant(leaver, SPOUSE, AccessScope.SLEEP, AccessLevel.VIEW, UUID.randomUUID(), NOW);
        fixture.depart(leaver);
        when(grants.findByAudienceMemberIdInAndRevokedAtIsNull(List.of(SPOUSE))).thenReturn(List.of(fromMe, fromLeaver));

        List<AccessGrantView> incoming = service.listIncoming(SPOUSE_ACCOUNT);

        assertThat(incoming).extracting(AccessGrantView::id).containsExactly(fromMe.getId());
    }

    @Test
    @DisplayName("an account with no membership has nothing shared with it")
    void incomingForNobodyIsEmpty() {
        assertThat(service.listIncoming(UUID.randomUUID())).isEmpty();
    }

    /* -------------------------------------------------------------- data rights */

    @Test
    @DisplayName("erasure deletes the member's own grants and revokes grants made to them")
    void eraseForMember() {
        AccessGrant received = AccessGrant.grant(SPOUSE, ME, AccessScope.ACTIVITY, AccessLevel.VIEW, SPOUSE_ACCOUNT, NOW.minusSeconds(60));
        when(grants.findByAudienceMemberIdAndRevokedAtIsNull(ME)).thenReturn(List.of(received));

        service.eraseForMember(ME);

        verify(grants).deleteBySubjectMemberId(ME);
        // The other person's sharing history is theirs; it is ended, not erased.
        assertThat(received.getRevokedAt()).isEqualTo(NOW);
        verify(grants).save(received);
        verify(grants, never()).delete(received);
    }

    @Test
    @DisplayName("the export includes revoked grants — the full history of who could see what")
    void exportIncludesRevoked() {
        AccessGrant revoked = AccessGrant.grant(ME, SPOUSE, AccessScope.BODY, AccessLevel.VIEW, ME_ACCOUNT, NOW.minusSeconds(600));
        revoked.revoke(NOW.minusSeconds(60));
        AccessGrant live = AccessGrant.grant(ME, SPOUSE, AccessScope.SLEEP, AccessLevel.VIEW, ME_ACCOUNT, NOW);
        when(grants.findBySubjectMemberIdOrderByGrantedAtDesc(ME)).thenReturn(List.of(live, revoked));

        assertThat(service.exportForSubject(ME)).extracting(AccessGrantView::revokedAt)
                .containsExactly(null, NOW.minusSeconds(60));
    }
}
