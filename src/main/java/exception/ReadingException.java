package exception;
public class ReadingException extends Exception {
	
	public ReadingException(String message){
		super(message);
	}
	
	public ReadingException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
