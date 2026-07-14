package cat.clicker.config;

import cat.clicker.config.Config.InputType;
import cat.clicker.config.Config.KeyBinding;
import cat.clicker.config.Config.Mode;

import java.util.ArrayList;
import java.util.List;

/**
 * Именованный профиль — самостоятельный набор настроек кликера.
 *
 * <p>В профиль входит всё, кроме главного выключателя {@link Config#enabled} и
 * указателя на активный профиль: они общие для всего приложения.
 */
public class Profile {

    /** Имя профиля; уникально в пределах конфига. */
    public String name;
    public KeyBinding hotkey;
    public KeyBinding emergencyStop;
    public Mode mode;
    public int delayMs;
    public List<KeyBinding> keys = new ArrayList<>();
    /** Держать главное окно поверх всех остальных окон. */
    public boolean alwaysOnTop;

    /** Имя профиля, создаваемого при первом запуске и при миграции старого конфига. */
    public static final String DEFAULT_NAME = "По умолчанию";

    /** Значения по умолчанию (см. §5.1 ТЗ). */
    public static Profile defaults(String name) {
        Profile p = new Profile();
        p.name = name;
        p.hotkey = new KeyBinding(InputType.KEYBOARD, "VC_F6");
        p.emergencyStop = new KeyBinding(InputType.KEYBOARD, "VC_ESCAPE");
        p.mode = Mode.HOLD;
        p.delayMs = Config.DEFAULT_DELAY_MS;
        p.keys = new ArrayList<>();
        p.keys.add(new KeyBinding(InputType.MOUSE, "BUTTON1"));
        p.alwaysOnTop = false;
        return p;
    }

    /** Глубокая копия — {@link KeyBinding} неизменяем, поэтому достаточно скопировать список. */
    public Profile copy() {
        Profile p = new Profile();
        p.name = name;
        p.hotkey = hotkey;
        p.emergencyStop = emergencyStop;
        p.mode = mode;
        p.delayMs = delayMs;
        p.keys = new ArrayList<>(keys);
        p.alwaysOnTop = alwaysOnTop;
        return p;
    }

    @Override
    public String toString() {
        return name;
    }
}
