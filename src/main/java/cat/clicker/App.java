package cat.clicker;

import cat.clicker.config.Config;
import cat.clicker.config.ConfigStore;
import cat.clicker.config.FatalConfigException;
import cat.clicker.core.Clicker;
import cat.clicker.core.HotkeyService;
import cat.clicker.ui.MainWindow;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AWTException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Точка входа ClickerCat: приглушает логирование JNativeHook, загружает конфиг
 * (со сценарием восстановления), регистрирует глобальный хук, поднимает окно и
 * гарантирует корректное завершение (§2, §3.1, §7, §8).
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        muteJNativeHookLogging();
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // системный L&F необязателен
        }

        ConfigStore store = new ConfigStore();
        Config config;
        try {
            config = store.loadOrRecover(new SwingRecoveryPrompts());
        } catch (FatalConfigException e) {
            JOptionPane.showMessageDialog(null,
                    "Конфигурация повреждена, восстановление отменено.\n"
                            + "Файл оставлен без изменений для ручного разбора.\n\n" + e.getMessage(),
                    "ClickerCat", JOptionPane.ERROR_MESSAGE);
            System.exit(2);
            return;
        }

        Clicker clicker;
        try {
            clicker = new Clicker();
        } catch (AWTException e) {
            JOptionPane.showMessageDialog(null,
                    "Не удалось инициализировать эмуляцию ввода (Robot): " + e.getMessage(),
                    "ClickerCat", JOptionPane.ERROR_MESSAGE);
            System.exit(3);
            return;
        }

        HotkeyService hotkeys = new HotkeyService();
        try {
            hotkeys.register();
        } catch (NativeHookException e) {
            JOptionPane.showMessageDialog(null,
                    "Не удалось включить глобальный перехват клавиатуры/мыши.\n\n"
                            + "На Linux это работает на X11; на Wayland глобальный хук "
                            + "ограничен или недоступен.\n\n"
                            + "Горячие клавиши работать не будут; доступен только ручной Старт/Стоп.\n\n"
                            + "Подробности: " + e.getMessage(),
                    "ClickerCat — глобальный хук недоступен", JOptionPane.WARNING_MESSAGE);
            // Продолжаем: окно открывается, ручное управление доступно.
        }

        registerShutdown(clicker, hotkeys);

        MainWindow window = new MainWindow(config, store, hotkeys, clicker);
        window.setVisible(true);
    }

    private static void registerShutdown(Clicker clicker, HotkeyService hotkeys) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                clicker.stop();
            } catch (RuntimeException ignored) {
                // best-effort
            }
            hotkeys.unregister();
        }, "clickercat-shutdown"));
    }

    /** Приглушить логгер JNativeHook (§8). */
    private static void muteJNativeHookLogging() {
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
    }

    /** Диалоги восстановления конфигурации (§5.2). */
    private static final class SwingRecoveryPrompts implements ConfigStore.RecoveryPrompts {
        @Override
        public boolean askRestoreFromBackup() {
            int r = JOptionPane.showConfirmDialog(null,
                    "Файл настроек повреждён. Восстановить из резервной копии?",
                    "ClickerCat", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            return r == JOptionPane.YES_OPTION;
        }

        @Override
        public boolean askResetToDefaults() {
            int r = JOptionPane.showConfirmDialog(null,
                    "Файл настроек повреждён. Сбросить к значениям по умолчанию и перезаписать файл?",
                    "ClickerCat", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            return r == JOptionPane.YES_OPTION;
        }
    }
}
