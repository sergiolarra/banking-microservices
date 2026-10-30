package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.application.usecase.CreateAccountUseCase;
import com.bank.accounts.application.usecase.GetAccountUseCase;
import com.bank.accounts.application.usecase.ListAccountsUseCase;
import com.bank.accounts.application.usecase.UpdateAccountUseCase;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.infrastructure.rest.dto.AccountCreateRequest;
import com.bank.accounts.infrastructure.rest.dto.AccountPatchRequest;
import com.bank.accounts.infrastructure.rest.dto.AccountResponse;
import com.bank.accounts.infrastructure.rest.dto.AccountUpdateRequest;
import com.bank.accounts.infrastructure.rest.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** F1: create, read and update accounts. There is no DELETE (F1-AC8). */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
class AccountController {

    private final CreateAccountUseCase createAccount;
    private final GetAccountUseCase getAccount;
    private final ListAccountsUseCase listAccounts;
    private final UpdateAccountUseCase updateAccount;
    private final AccountRestMapper mapper;

    @PostMapping
    ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        Account account = createAccount.execute(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{accountNumber}")
                .buildAndExpand(account.getAccountNumber())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(account));
    }

    @GetMapping
    PageResponse<AccountResponse> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE)
            @Min(value = 1, message = "size must be between 1 and 100")
            @Max(value = PageQuery.MAX_SIZE, message = "size must be between 1 and 100") int size) {
        return PageResponse.from(listAccounts.execute(clientId, new PageQuery(page, size)).map(mapper::toResponse));
    }

    @GetMapping("/{accountNumber}")
    AccountResponse get(@PathVariable String accountNumber) {
        return mapper.toResponse(getAccount.execute(accountNumber));
    }

    @PutMapping("/{accountNumber}")
    AccountResponse replace(@PathVariable String accountNumber, @Valid @RequestBody AccountUpdateRequest request) {
        return mapper.toResponse(updateAccount.execute(accountNumber, mapper.toChanges(request)));
    }

    @PatchMapping("/{accountNumber}")
    AccountResponse patch(@PathVariable String accountNumber, @Valid @RequestBody AccountPatchRequest request) {
        return mapper.toResponse(updateAccount.execute(accountNumber, mapper.toChanges(request)));
    }
}
