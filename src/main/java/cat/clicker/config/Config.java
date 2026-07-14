package cat.clicker.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * POJO-модель всех настроек приложения: список {@link Profile} плюс то немногое,
 * что общее для всего приложения.
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

    /** Профили; список всегда непустой. */
    public List<Profile> profiles = new ArrayList<>();
    /** Имя активного профиля. */
    public String activeProfile;
    /**
     * Главный выключатель всего механизма (§ кнопка «Вкл/Выкл»). Когда {@code false},
     * кликер полностью приостановлен: горячие клавиши не срабатывают и не мешают работе.
     */
    public boolean enabled = true;

    public static final int MIN_DELAY_MS = 0;
    public static final int MAX_DELAY_MS = 10_000;
    public static final int DEFAULT_DELAY_MS = 100;

    /** Значения по умолчанию (см. §5.1 ТЗ): один профиль. */
    public static Config defaults() {
        Config c = new Config();
        c.profiles.add(Profile.defaults(Profile.DEFAULT_NAME));
        c.activeProfile = Profile.DEFAULT_NAME;
        c.enabled = true;
        return c;
    }

    /** Активный профиль; если имя не найдено (правка файла вручную) — первый по списку. */
    public Profile active() {
        for (Profile p : profiles) {
            if (p.name.equals(activeProfile)) {
                return p;
            }
        }
        return profiles.get(0);
    }

    /** Есть ли уже профиль с таким именем (имена уникальны). */
    public boolean hasProfile(String name) {
        for (Profile p : profiles) {
            if (p.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
