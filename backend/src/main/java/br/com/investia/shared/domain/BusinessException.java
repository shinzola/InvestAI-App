package br.com.investia.shared.domain;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio (422). */
public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
