package br.com.investia.shared.domain;

import org.springframework.http.HttpStatus;

/** Recurso não encontrado (404). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
