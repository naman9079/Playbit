package com.playbit.exception;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public ApiException(int code, String message) { super(message); status = HttpStatus.valueOf(code); }
}
