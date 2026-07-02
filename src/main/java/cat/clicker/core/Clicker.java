package cat.clicker.core;

import cat.clicker.config.Config.InputType;
import cat.clicker.config.Config.KeyBinding;
import cat.clicker.input.KeyMapper;

import java.awt.AWTException;
import java.awt.Robot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Рабочий поток кликера (§6). За одну итерацию нажимаются все элементы набора,
 * затем отпускаются, затем выдерживается задержка.
 *
 * <p>Курсор не перемещается — клики идут в текущей позиции (§3.5).
 * При остановке гарантированно отпускаются все нажатые клавиши/кнопки.
 */
public class Clicker {

    private final Robot robot;
    private final Object lock = new Object();
    private Thread worker;
    private volatile boolean active;

    public Clicker() throws AWTException {
        this.robot = new Robot();
        this.robot.setAutoWaitForIdle(false);
    }

    public boolean isRunning() {
        return active;
    }

    /** Запуск цикла. Повторный вызов при уже активном кликере игнорируется. */
    public void start(List<KeyBinding> keys, int delayMs) {
        synchronized (lock) {
            if (active) {
                return;
            }
            List<KeyBinding> snapshot = new ArrayList<>(keys);
            if (snapshot.isEmpty()) {
                return;
            }
            active = true;
            worker = new Thread(() -> run(snapshot, delayMs), "clickercat-worker");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** Немедленная остановка с ожиданием завершения потока (без «залипших» клавиш). */
    public void stop() {
        Thread t;
        synchronized (lock) {
            if (!active) {
                return;
            }
            active = false;
            t = worker;
            worker = null;
        }
        if (t != null) {
            t.interrupt();
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run(List<KeyBinding> keys, int delayMs) {
        // Стек нажатых в текущей итерации — отпускаем в обратном порядке в finally.
        Deque<KeyBinding> pressed = new ArrayDeque<>();
        try {
            while (active && !Thread.currentThread().isInterrupted()) {
                // press всех элементов
                for (KeyBinding kb : keys) {
                    if (!active) {
                        break;
                    }
                    press(kb);
                    pressed.push(kb);
                }
                // release всех элементов
                while (!pressed.isEmpty()) {
                    release(pressed.pop());
                }
                // задержка между итерациями
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Гарантированно отпустить всё, что осталось нажатым.
            while (!pressed.isEmpty()) {
                try {
                    release(pressed.pop());
                } catch (RuntimeException ignored) {
                    // продолжаем отпускать остальные
                }
            }
            // Поток завершился (штатно, по ошибке или прерыванию) — снять флаг
            // активности, но только если нас не подменил новый worker.
            synchronized (lock) {
                if (worker == Thread.currentThread()) {
                    active = false;
                    worker = null;
                }
            }
        }
    }

    private void press(KeyBinding kb) {
        if (kb.type == InputType.KEYBOARD) {
            robot.keyPress(KeyMapper.toRobotKey(kb.code));
        } else {
            robot.mousePress(KeyMapper.toRobotButtonMask(kb.code));
        }
    }

    private void release(KeyBinding kb) {
        if (kb.type == InputType.KEYBOARD) {
            robot.keyRelease(KeyMapper.toRobotKey(kb.code));
        } else {
            robot.mouseRelease(KeyMapper.toRobotButtonMask(kb.code));
        }
    }
}
