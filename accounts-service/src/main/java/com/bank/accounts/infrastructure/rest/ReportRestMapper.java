package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.domain.model.AccountStatement;
import com.bank.accounts.infrastructure.rest.dto.AccountStatementResponse;
import org.mapstruct.Mapper;

/** Domain account statement → HTTP body; nested types are mapped by name. */
@Mapper
interface ReportRestMapper {

    AccountStatementResponse toResponse(AccountStatement statement);
}
