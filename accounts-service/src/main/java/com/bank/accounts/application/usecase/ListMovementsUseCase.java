package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.domain.port.MovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F2-AC3: lists movements, newest first, optionally of one account. */
@Service
public class ListMovementsUseCase {

    private final MovementRepository movements;

    public ListMovementsUseCase(MovementRepository movements) {
        this.movements = movements;
    }

    /** @param accountNumber optional filter */
    @Transactional(readOnly = true)
    public PagedResult<Movement> execute(String accountNumber, PageQuery query) {
        return movements.findAll(accountNumber, query);
    }
}
