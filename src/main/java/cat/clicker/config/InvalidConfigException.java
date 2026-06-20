package cat.clicker.config;

/** Конфигурационный файл не читается или не проходит валидацию (см. §5.2). */
public class InvalidConfigException extends Exception {

    public InvalidConfigException(String message) {
        super(message);
    }

    public InvalidConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
