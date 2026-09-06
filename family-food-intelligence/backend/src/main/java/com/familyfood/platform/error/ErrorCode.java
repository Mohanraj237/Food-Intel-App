package com.familyfood.platform.error;

import org.springframework.http.HttpStatus;

/**
 * The closed set of failures this API can report.
 *
 * <p>{@code messageKey} resolves in the frontend locale bundle, so the wording a user
 * sees lives with the rest of the copy and is reviewed against the banned-vocabulary
 * rule (D-004, D-019) rather than being scattered through Java strings.
 *
 * <p>Note the deliberate separation of {@link #PRODUCT_NOT_FOUND} from
 * {@link #SERVICE_UNAVAILABLE} and {@link #AI_UNAVAILABLE}: "we do not have this
 * product" and "we could not reach a service" are different truths and the product
 * must never present one as the other (Constitution §46).
 */
public enum ErrorCode {

    // ---- request / validation ----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "error.validation_failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "error.malformed_request"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.unsupported_media_type"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "error.payload_too_large"),

    // ---- authentication ----
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "error.authentication_required"),
    /* Deliberately identical for unknown email and wrong password — no enumeration. */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "error.invalid_credentials"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "error.token_expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "error.token_invalid"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "error.account_suspended"),

    // ---- authorization ----
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "error.access_denied"),
    NOT_HOUSEHOLD_MEMBER(HttpStatus.FORBIDDEN, "error.not_household_member"),
    NOT_A_GUARDIAN(HttpStatus.FORBIDDEN, "error.not_a_guardian"),
    NOT_AN_OPERATOR(HttpStatus.FORBIDDEN, "error.not_an_operator"),

    // ---- consent (D-001, D-018) ----
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "error.consent_required"),
    GUARDIAN_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "error.guardian_consent_required"),
    CONSENT_WITHDRAWN(HttpStatus.FORBIDDEN, "error.consent_withdrawn"),
    CONSENT_POLICY_SUPERSEDED(HttpStatus.CONFLICT, "error.consent_policy_superseded"),

    // ---- resources ----
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.resource_not_found"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.product_not_found"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "error.email_already_registered"),
    ALREADY_HOUSEHOLD_MEMBER(HttpStatus.CONFLICT, "error.already_household_member"),
    INVITATION_INVALID(HttpStatus.GONE, "error.invitation_invalid"),
    CONTRIBUTION_ALREADY_REVIEWED(HttpStatus.CONFLICT, "error.contribution_already_reviewed"),

    // ---- product data (Constitution §77) ----
    BARCODE_AMBIGUOUS(HttpStatus.CONFLICT, "error.barcode_ambiguous"),
    PRODUCT_DATA_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY, "error.product_data_insufficient"),
    PRODUCT_DATA_IMPLAUSIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "error.product_data_implausible"),
    EXTRACTION_NOT_CONFIRMED(HttpStatus.CONFLICT, "error.extraction_not_confirmed"),

    // ---- capture / OCR ----
    IMAGE_UNREADABLE(HttpStatus.UNPROCESSABLE_ENTITY, "error.image_unreadable"),
    IMAGE_REJECTED(HttpStatus.BAD_REQUEST, "error.image_rejected"),
    OCR_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "error.ocr_failed"),

    // ---- scoring ----
    NO_ACTIVE_RULE_SET(HttpStatus.SERVICE_UNAVAILABLE, "error.no_active_rule_set"),

    // ---- throttling ----
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "error.rate_limited"),

    // ---- degraded dependencies (never conflated with "not found") ----
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.ai_unavailable"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.storage_unavailable"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.service_unavailable"),

    // ---- catch-all ----
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");

    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

    public HttpStatus status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }

    /** Stable, documented URI used as the RFC 7807 {@code type}. */
    public String typeUri() {
        return "https://familyfood.app/problems/" + name().toLowerCase().replace('_', '-');
    }
}
