package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.application.command.AccountChanges;
import com.bank.accounts.application.command.CreateAccountCommand;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.infrastructure.rest.dto.AccountCreateRequest;
import com.bank.accounts.infrastructure.rest.dto.AccountPatchRequest;
import com.bank.accounts.infrastructure.rest.dto.AccountResponse;
import com.bank.accounts.infrastructure.rest.dto.AccountUpdateRequest;
import com.bank.accounts.infrastructure.rest.dto.MovementResponse;
import org.mapstruct.Mapper;

/** Translates between HTTP DTOs and application commands / domain objects. */
@Mapper
interface AccountRestMapper {

    CreateAccountCommand toCommand(AccountCreateRequest request);

    AccountChanges toChanges(AccountUpdateRequest request);

    AccountChanges toChanges(AccountPatchRequest request);

    AccountResponse toResponse(Account account);

    MovementResponse toResponse(Movement movement);
}
