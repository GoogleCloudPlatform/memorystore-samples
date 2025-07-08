package app;

/**
 * Exception thrown when a service operation fails.
 */
class DataControllerException extends RuntimeException {

  /**
   * Constructs a new DataControllerException with the specified detail message.
   *
   * @param message the detail message
   */
  DataControllerException(final String message) {
    super(message);
  }

  /**
   * Constructs a new DataControllerException
   * with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause   the cause of the exception
   */
  DataControllerException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
