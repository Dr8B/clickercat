package cat.clicker.config;

/**
 * Конфиг повреждён, восстановить нечем и пользователь отказался от сброса.
 * Приложение обязано завершиться с ненулевым кодом, не перезаписывая файл (§5.2 п.3).
 */
public class FatalConfigException extends Exception {

    public FatalConfigException(String message) {
        super(message);
    }
}
