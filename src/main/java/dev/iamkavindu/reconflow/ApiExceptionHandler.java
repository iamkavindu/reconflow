package dev.iamkavindu.reconflow;

import dev.iamkavindu.reconflow.tenancy.OperationNotAuthorizedException;
import java.util.NoSuchElementException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OperationNotAuthorizedException.class)
    ProblemDetail forbidden() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "This operation is not permitted for your role.");
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail missing() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested record was not found.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request contains invalid values.");
    }

    @ExceptionHandler({IllegalStateException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The record state does not allow this change.");
    }
}
