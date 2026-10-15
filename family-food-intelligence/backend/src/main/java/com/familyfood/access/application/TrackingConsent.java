package com.familyfood.access.application;

import com.familyfood.consent.application.ConsentGate;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consent for wellness tracking (D-048), resolved to the right purpose for the subject.
 *
 * <p>An adult grants {@code HEALTH_TRACKING} for themselves; a guardian grants
 * {@code CHILD_WELLNESS_TRACKING} for a child. Before W1 no purpose covered tracking at all,
 * and a child's meals, sleep and activity were being recorded against the only child purpose
 * there was — whose own text says "no consumption tracking" (D-042, defect 2).
 *
 * <p>Consent gates <em>collection</em> (every write of tracking data) and <em>disclosure</em>
 * (every cross-member read). A subject's own reads of what is already stored stay available
 * after withdrawal, so that they can see, export and erase it.
 */
@Component
public class TrackingConsent {

    public static final String ADULT_PURPOSE = "HEALTH_TRACKING";
    public static final String CHILD_PURPOSE = "CHILD_WELLNESS_TRACKING";

    private static final String CHILD_MEMBER_TYPE = "CHILD";

    private final ConsentGate consentGate;
    private final HouseholdAccess households;

    public TrackingConsent(ConsentGate consentGate, HouseholdAccess households) {
        this.consentGate = consentGate;
        this.households = households;
    }

    public String purposeFor(MemberInfo subject) {
        return CHILD_MEMBER_TYPE.equals(subject.memberType()) ? CHILD_PURPOSE : ADULT_PURPOSE;
    }

    /** Whether tracking data may currently be collected for, or disclosed about, the subject. */
    public boolean isActive(MemberInfo subject) {
        return consentGate.isActive(subject.id(), purposeFor(subject));
    }

    public boolean isActive(UUID subjectMemberId) {
        return households.findMember(subjectMemberId).map(this::isActive).orElse(false);
    }

    /**
     * Throws {@code CONSENT_REQUIRED} or {@code CONSENT_WITHDRAWN} (and audits the refusal)
     * unless the subject's tracking purpose is active. Call at the point of processing.
     */
    public void require(MemberInfo subject) {
        consentGate.require(subject.id(), purposeFor(subject));
    }

    public void require(UUID subjectMemberId) {
        require(households.findMember(subjectMemberId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND)));
    }
}
