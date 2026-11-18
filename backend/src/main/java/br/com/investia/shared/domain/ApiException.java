package br.com.investia.shared.domain;

import org.springframework.http.HttpStatus;

/** Exceção base de domínio/aplicação mapeada para Problem Details (RFC 7807). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static NotFoundException notFound(String message) {
        return new NotFoundException(message);
    }

    public static BusinessException business(String message) {
        return new BusinessException(message);
    }
}
