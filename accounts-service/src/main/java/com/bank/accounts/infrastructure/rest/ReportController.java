package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.application.usecase.GenerateAccountStatementUseCase;
import com.bank.accounts.infrastructure.rest.dto.AccountStatementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * F4: {@code GET /reports?date=yyyy-MM-dd,yyyy-MM-dd&client={clientId}} returns the
 * account statement as JSON (F4-AC7).
 */
@RestController
@RequestMapping(path = "/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
class ReportController {

    private final GenerateAccountStatementUseCase generateAccountStatement;
    private final ReportRestMapper mapper;

    @GetMapping
    AccountStatementResponse accountStatement(@RequestParam String date, @RequestParam UUID client) {
        return mapper.toResponse(generateAccountStatement.execute(client, DateRangeParameter.parse(date)));
    }
}
