package com.familyfood.identity.application;

import com.familyfood.identity.domain.UserAccount;
import com.familyfood.identity.infra.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OperatorAccessImpl implements OperatorAccess {

    private final UserAccountRepository accounts;

    public OperatorAccessImpl(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean isOperator(UUID accountId) {
        return accounts.findById(accountId)
                .map(a -> a.getRole() == UserAccount.OperatorRole.OPS_REVIEWER && a.isUsable())
                .orElse(false);
    }
}
