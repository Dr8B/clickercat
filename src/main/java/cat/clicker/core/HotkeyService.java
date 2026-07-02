package cat.clicker.core;

import cat.clicker.config.Config.KeyBinding;
import cat.clicker.input.KeyMapper;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Глобальный перехват клавиатуры/мыши через JNativeHook (§3.2, §3.7).
 *
 * <p>Различает два режима: обычная обработка (триггер / аварийная остановка) и
 * «захват следующего нажатия» для назначения клавиши в UI. События приходят на
 * фоновом потоке диспетчера JNativeHook — не на EDT.
 */
public class HotkeyService implements NativeKeyListener, NativeMouseListener {

    /** Реакции на глобальные события. Вызываются НЕ на EDT. */
    public interface Listener {
        void onTriggerPressed();

        void onTriggerReleased();

        void onEmergencyStop();
    }

    private volatile KeyBinding hotkey;
    private volatile KeyBinding emergencyStop;
    private volatile Listener listener;
    private volatile Consumer<KeyBinding> captureCallback;

    /** Главный выключатель: при {@code false} триггер и аварийная остановка не обрабатываются. */
    private volatile boolean enabled = true;

    /** Уже зажатые привязки — для подавления автоповтора ОС. */
    private final Set<KeyBinding> down = new HashSet<>();

    private boolean registered;

    public void register() throws NativeHookException {
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
        GlobalScreen.addNativeMouseListener(this);
        registered = true;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void unregister() {
        if (!registered) {
            return;
        }
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            System.err.println("ClickerCat: не удалось дерегистрировать хук: " + e.getMessage());
        } finally {
            registered = false;
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setHotkey(KeyBinding hotkey) {
        this.hotkey = hotkey;
    }

    public void setEmergencyStop(KeyBinding emergencyStop) {
        this.emergencyStop = emergencyStop;
    }

    /**
     * Включить/выключить обработку триггера и аварийной остановки. Захват клавиш
     * для назначения продолжает работать, чтобы настройки можно было менять и в паузе.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Перехватить следующее нажатие клавиши/кнопки и вернуть его как {@link KeyBinding}.
     * Это нажатие не передаётся в обычную обработку (триггер/стоп).
     */
    public void captureNext(Consumer<KeyBinding> callback) {
        this.captureCallback = callback;
    }

    public void cancelCapture() {
        this.captureCallback = null;
    }

    // ---- диспетчеризация ----------------------------------------------------

    private void handlePress(KeyBinding binding) {
        Consumer<KeyBinding> capture = captureCallback;
        if (capture != null) {
            captureCallback = null;
            capture.accept(binding);
            return;
        }

        // Главный выключатель: механизм приостановлен — триггер/стоп игнорируются.
        if (!enabled) {
            return;
        }

        // Подавляем автоповтор: реагируем только на переход «отпущено → нажато».
        synchronized (down) {
            if (!down.add(binding)) {
                return;
            }
        }

        Listener l = listener;
        if (l == null) {
            return;
        }
        if (binding.equals(emergencyStop)) {
            l.onEmergencyStop();
            return;
        }
        if (binding.equals(hotkey)) {
            l.onTriggerPressed();
        }
    }

    private void handleRelease(KeyBinding binding) {
        synchronized (down) {
            down.remove(binding);
        }
        if (captureCallback != null) {
            return;
        }
        if (!enabled) {
            return;
        }
        Listener l = listener;
        if (l != null && binding.equals(hotkey)) {
            l.onTriggerReleased();
        }
    }

    // ---- NativeKeyListener --------------------------------------------------

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        handlePress(KeyMapper.fromNativeKey(e));
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        handleRelease(KeyMapper.fromNativeKey(e));
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // не используется
    }

    // ---- NativeMouseListener ------------------------------------------------

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
        handlePress(KeyMapper.fromNativeMouse(e));
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
        handleRelease(KeyMapper.fromNativeMouse(e));
    }

    @Override
    public void nativeMouseClicked(NativeMouseEvent e) {
        // не используется
    }
}
