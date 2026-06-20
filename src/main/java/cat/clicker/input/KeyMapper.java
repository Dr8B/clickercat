package cat.clicker.input;

import cat.clicker.config.Config.InputType;
import cat.clicker.config.Config.KeyBinding;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Конвертер кодов JNativeHook ↔ {@link java.awt.Robot} (§«Маппинг кодов»).
 *
 * <p>Клавиатура: имя константы {@code NativeKeyEvent.VC_*} ↔ {@code KeyEvent.VK_*}.
 * Мышь: номер кнопки JNativeHook ↔ маска {@code InputEvent.BUTTONx_DOWN_MASK}.
 *
 * <p>Раскладка специально не обрабатывается: эмулируется физическая клавиша.
 */
public final class KeyMapper {

    /** Код JNativeHook (int) → имя константы {@code VC_*}. */
    private static final Map<Integer, String> VC_CODE_TO_NAME = new HashMap<>();
    /** Имя {@code VK_*} → код {@code KeyEvent.VK_*} (int). */
    private static final Map<String, Integer> VK_NAME_TO_CODE = new HashMap<>();
    /** Имя {@code VC_*} → код {@code KeyEvent.VK_*} для случаев, где имена расходятся. */
    private static final Map<String, Integer> VC_NAME_TO_VK = new HashMap<>();

    static {
        // Имена констант JNativeHook по их числовому коду.
        for (Field f : NativeKeyEvent.class.getFields()) {
            if (isPublicStaticIntConst(f) && f.getName().startsWith("VC_")) {
                try {
                    VC_CODE_TO_NAME.putIfAbsent(f.getInt(null), f.getName());
                } catch (IllegalAccessException ignored) {
                    // публичное static-поле — недостижимо
                }
            }
        }
        // Все VK_-константы AWT по имени.
        for (Field f : KeyEvent.class.getFields()) {
            if (isPublicStaticIntConst(f) && f.getName().startsWith("VK_")) {
                try {
                    VK_NAME_TO_CODE.put(f.getName(), f.getInt(null));
                } catch (IllegalAccessException ignored) {
                    // недостижимо
                }
            }
        }
        // Явные соответствия там, где имена VC_* и VK_* отличаются.
        overrides();
    }

    private KeyMapper() {
    }

    private static void overrides() {
        put("VC_BACKSPACE", KeyEvent.VK_BACK_SPACE);
        put("VC_BACKQUOTE", KeyEvent.VK_BACK_QUOTE);
        put("VC_BACK_QUOTE", KeyEvent.VK_BACK_QUOTE);
        // Модификаторы JNativeHook различают левый/правый — Robot их не различает.
        put("VC_SHIFT_L", KeyEvent.VK_SHIFT);
        put("VC_SHIFT_R", KeyEvent.VK_SHIFT);
        put("VC_CONTROL_L", KeyEvent.VK_CONTROL);
        put("VC_CONTROL_R", KeyEvent.VK_CONTROL);
        put("VC_ALT_L", KeyEvent.VK_ALT);
        put("VC_ALT_R", KeyEvent.VK_ALT);
        put("VC_META_L", KeyEvent.VK_META);
        put("VC_META_R", KeyEvent.VK_META);
        // Цифровая клавиатура.
        put("VC_KP_0", KeyEvent.VK_NUMPAD0);
        put("VC_KP_1", KeyEvent.VK_NUMPAD1);
        put("VC_KP_2", KeyEvent.VK_NUMPAD2);
        put("VC_KP_3", KeyEvent.VK_NUMPAD3);
        put("VC_KP_4", KeyEvent.VK_NUMPAD4);
        put("VC_KP_5", KeyEvent.VK_NUMPAD5);
        put("VC_KP_6", KeyEvent.VK_NUMPAD6);
        put("VC_KP_7", KeyEvent.VK_NUMPAD7);
        put("VC_KP_8", KeyEvent.VK_NUMPAD8);
        put("VC_KP_9", KeyEvent.VK_NUMPAD9);
        put("VC_KP_DIVIDE", KeyEvent.VK_DIVIDE);
        put("VC_KP_MULTIPLY", KeyEvent.VK_MULTIPLY);
        put("VC_KP_SUBTRACT", KeyEvent.VK_SUBTRACT);
        put("VC_KP_ADD", KeyEvent.VK_ADD);
        put("VC_KP_ENTER", KeyEvent.VK_ENTER);
        put("VC_KP_SEPARATOR", KeyEvent.VK_DECIMAL);
    }

    private static void put(String vcName, int vk) {
        VC_NAME_TO_VK.put(vcName, vk);
    }

    private static boolean isPublicStaticIntConst(Field f) {
        int m = f.getModifiers();
        return Modifier.isPublic(m) && Modifier.isStatic(m) && f.getType() == int.class;
    }

    // ---- JNativeHook → KeyBinding ------------------------------------------

    public static KeyBinding fromNativeKey(NativeKeyEvent e) {
        String name = VC_CODE_TO_NAME.get(e.getKeyCode());
        if (name == null) {
            name = "VC_" + e.getKeyCode(); // неизвестный код — сохраняем как есть
        }
        return new KeyBinding(InputType.KEYBOARD, name);
    }

    public static KeyBinding fromNativeMouse(NativeMouseEvent e) {
        return new KeyBinding(InputType.MOUSE, "BUTTON" + e.getButton());
    }

    // ---- KeyBinding → Robot -------------------------------------------------

    /** @return код {@code KeyEvent.VK_*} для нажатия клавиатуры Robot-ом. */
    public static int toRobotKey(String vcCode) {
        Integer override = VC_NAME_TO_VK.get(vcCode);
        if (override != null) {
            return override;
        }
        if (vcCode.startsWith("VC_")) {
            Integer vk = VK_NAME_TO_CODE.get("VK_" + vcCode.substring(3));
            if (vk != null) {
                return vk;
            }
        }
        throw new IllegalArgumentException("Нет соответствия Robot для клавиши: " + vcCode);
    }

    /**
     * @return маска {@code InputEvent.BUTTONx_DOWN_MASK} для нажатия мыши Robot-ом.
     *
     * <p>Нумерация JNativeHook (1=левая, 2=правая, 3=средняя) приводится к
     * семантике AWT, где BUTTON1=левая, BUTTON2=средняя, BUTTON3=правая.
     */
    public static int toRobotButtonMask(String code) {
        int button = parseButton(code);
        return switch (button) {
            case 1 -> InputEvent.BUTTON1_DOWN_MASK; // левая
            case 2 -> InputEvent.BUTTON3_DOWN_MASK; // правая
            case 3 -> InputEvent.BUTTON2_DOWN_MASK; // средняя
            default -> InputEvent.getMaskForButton(button);
        };
    }

    private static int parseButton(String code) {
        if (!code.startsWith("BUTTON")) {
            throw new IllegalArgumentException("Некорректный код кнопки мыши: " + code);
        }
        try {
            return Integer.parseInt(code.substring("BUTTON".length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный код кнопки мыши: " + code, e);
        }
    }

    // ---- отображение в UI ---------------------------------------------------

    /** Человекочитаемое имя привязки, например «Key SPACE» / «Mouse LEFT». */
    public static String displayName(KeyBinding kb) {
        if (kb.type == InputType.MOUSE) {
            return "Mouse " + mouseLabel(kb.code);
        }
        String name = kb.code.startsWith("VC_") ? kb.code.substring(3) : kb.code;
        return "Key " + name;
    }

    private static String mouseLabel(String code) {
        int button;
        try {
            button = parseButton(code);
        } catch (IllegalArgumentException e) {
            return code;
        }
        return switch (button) {
            case 1 -> "LEFT";
            case 2 -> "RIGHT";
            case 3 -> "MIDDLE";
            default -> "BUTTON" + button;
        };
    }
}
