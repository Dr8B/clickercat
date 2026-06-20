package cat.clicker.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * POJO-модель всех настроек приложения.
 *
 * <p>Содержит вложенные перечисления {@link InputType} / {@link Mode} и тип
 * {@link KeyBinding} — единое представление «клавиша клавиатуры или кнопка мыши».
 */
public class Config {

    /** Источник события: клавиатура либо мышь. */
    public enum InputType {
        KEYBOARD,
        MOUSE
    }

    /** Режим срабатывания триггера. */
    public enum Mode {
        /** Кликер работает, пока клавиша зажата. */
        HOLD,
        /** Нажатие включает, повторное — выключает. */
        TOGGLE
    }

    /**
     * Привязка к конкретной клавише/кнопке.
     *
     * <p>{@code code} хранится в кодировке JNativeHook: для клавиатуры — имя
     * константы {@code VC_*} (например {@code VC_F6}), для мыши — {@code BUTTONn}
     * (например {@code BUTTON1} — левая кнопка).
     */
    public static final class KeyBinding {
        public final InputType type;
        public final String code;

        public KeyBinding(InputType type, String code) {
            this.type = Objects.requireNonNull(type, "type");
            this.code = Objects.requireNonNull(code, "code");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof KeyBinding other)) {
                return false;
            }
            return type == other.type && code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, code);
        }

        @Override
        public String toString() {
            return type + "/" + code;
        }
    }

    public KeyBinding hotkey;
    public KeyBinding emergencyStop;
    public Mode mode;
    public int delayMs;
    public List<KeyBinding> keys = new ArrayList<>();

    public static final int MIN_DELAY_MS = 0;
    public static final int MAX_DELAY_MS = 10_000;
    public static final int DEFAULT_DELAY_MS = 100;

    /** Значения по умолчанию (см. §5.1 ТЗ). */
    public static Config defaults() {
        Config c = new Config();
        c.hotkey = new KeyBinding(InputType.KEYBOARD, "VC_F6");
        c.emergencyStop = new KeyBinding(InputType.KEYBOARD, "VC_ESCAPE");
        c.mode = Mode.HOLD;
        c.delayMs = DEFAULT_DELAY_MS;
        c.keys = new ArrayList<>();
        c.keys.add(new KeyBinding(InputType.MOUSE, "BUTTON1"));
        return c;
    }

    /** Глубокая копия — UI работает с собственным экземпляром настроек. */
    public Config copy() {
        Config c = new Config();
        c.hotkey = hotkey;
        c.emergencyStop = emergencyStop;
        c.mode = mode;
        c.delayMs = delayMs;
        c.keys = new ArrayList<>(keys);
        return c;
    }
}
