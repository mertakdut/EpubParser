package exception;
public class OutOfPagesException extends Exception {
	
	public OutOfPagesException(String message){
		super(message);
	}
	
	public OutOfPagesException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
