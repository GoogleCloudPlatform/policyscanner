package com.google.cloud.security.scanner.exceptions;

public class PolicyScannerDataflowException extends RuntimeException {
  public PolicyScannerDataflowException(String message) {
    super(message);
  }

  public PolicyScannerDataflowException(String message, Throwable cause) {
    super(message, cause);
  }
}
