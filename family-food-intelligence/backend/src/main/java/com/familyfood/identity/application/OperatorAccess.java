package com.familyfood.identity.application;

import java.util.UUID;

/**
 * The only way another module may learn whether an account holds the ops-review role.
 * Mirrors {@code household.application.HouseholdAccess} (D-023): callers depend on this
 * published interface, never on {@code identity.domain}/{@code infra} directly.
 */
public interface OperatorAccess {

    boolean isOperator(UUID accountId);
}
