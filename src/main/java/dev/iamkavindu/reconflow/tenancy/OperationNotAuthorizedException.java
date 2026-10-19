package dev.iamkavindu.reconflow.tenancy;

public class OperationNotAuthorizedException extends RuntimeException {

    public OperationNotAuthorizedException(String message) {
        super(message);
    }
}
