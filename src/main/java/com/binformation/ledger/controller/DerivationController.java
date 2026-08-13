package com.binformation.ledger.controller;

import com.binformation.ledger.dto.derivation.DerivationDetailDto;
import com.binformation.ledger.dto.derivation.DerivationSaveRequest;
import com.binformation.ledger.service.DerivationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DerivationController {

    private final DerivationService derivationService;

    public DerivationController(DerivationService derivationService) {
        this.derivationService = derivationService;
    }

    @GetMapping("/assets/{assetId}/derivations")
    public List<DerivationDetailDto> listByAsset(@PathVariable Long assetId) {
        return derivationService.listByOutputAsset(assetId);
    }

    @PostMapping("/assets/{assetId}/derivations")
    @ResponseStatus(HttpStatus.CREATED)
    public DerivationDetailDto create(
            @PathVariable Long assetId,
            @Valid @RequestBody DerivationSaveRequest request) {
        return derivationService.create(assetId, request);
    }

    @GetMapping("/derivations/{id}")
    public DerivationDetailDto get(@PathVariable Long id) {
        return derivationService.getById(id);
    }

    @PutMapping("/derivations/{id}")
    public DerivationDetailDto update(
            @PathVariable Long id,
            @Valid @RequestBody DerivationSaveRequest request) {
        return derivationService.update(id, request);
    }

    @DeleteMapping("/derivations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        derivationService.delete(id);
    }
}
