package net.gisnas.oystein.ibm;

/**
 * Throwable-utilities shamelessly stolen from guava-libraries
 * http://code.google.com/p/guava-libraries
 */
public class Throwables {

	  /**
	   * Returns the innermost cause of {@code throwable}. The first throwable in a
	   * chain provides context from when the error or exception was initially
	   * detected. Example usage:
	   * <pre>
	   *   assertEquals("Unable to assign a customer id",
	   *       Throwables.getRootCause(e).getMessage());
	   * </pre>
	   */
	  public static Throwable getRootCause(Throwable throwable) {
	    Throwable cause;
	    while ((cause = throwable.getCause()) != null) {
	      throwable = cause;
	    }
	    return throwable;
	  }

}