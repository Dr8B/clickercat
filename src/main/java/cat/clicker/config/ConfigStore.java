package cat.clicker.config;

import cat.clicker.config.Config.InputType;
import cat.clicker.config.Config.KeyBinding;
import cat.clicker.config.Config.Mode;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Загрузка/сохранение конфигурации в YAML с резервным копированием (§3.9, §5).
 *
 * <p>Файлы располагаются в рабочей директории процесса ({@code new File("./")}).
 */
public class ConfigStore {

    /** UI-колбэки для сценария восстановления повреждённого конфига (§5.2). */
    public interface RecoveryPrompts {
        /** «Файл настроек повреждён. Восстановить из резервной копии?» */
        boolean askRestoreFromBackup();

        /** «Файл настроек повреждён. Сбросить к значениям по умолчанию и перезаписать файл?» */
        boolean askResetToDefaults();
    }

    private final File configFile;
    private final File backupFile;

    public ConfigStore() {
        this(new File("./clickercat.cfg.yaml"), new File("./clickercat.cfg.yaml.bak"));
    }

    public ConfigStore(File configFile, File backupFile) {
        this.configFile = configFile;
        this.backupFile = backupFile;
    }

    /**
     * Загрузка при старте с полным сценарием восстановления (§3.1, §5.2).
     *
     * @throws FatalConfigException конфиг повреждён, восстановить нечем и пользователь
     *                              отказался от сброса — приложение должно завершиться.
     */
    public Config loadOrRecover(RecoveryPrompts prompts) throws FatalConfigException {
        // §3.1: файла нет — берём значения по умолчанию и записываем.
        if (!configFile.exists()) {
            Config defaults = Config.defaults();
            saveQuietly(defaults);
            return defaults;
        }

        try {
            return read(configFile);
        } catch (InvalidConfigException primaryBroken) {
            return recover(prompts);
        }
    }

    private Config recover(RecoveryPrompts prompts) throws FatalConfigException {
        // §5.2 п.1: бэкап существует и валиден — предложить восстановление.
        Config fromBackup = null;
        if (backupFile.exists()) {
            try {
                fromBackup = read(backupFile);
            } catch (InvalidConfigException backupBroken) {
                fromBackup = null; // п.2: невалидный бэкап трактуется как «бэкапа нет».
            }
        }

        if (fromBackup != null && prompts.askRestoreFromBackup()) {
            // Бэкап становится основным конфигом; повреждённый файл перезаписывается.
            // Пишем напрямую, чтобы НЕ затереть валидный бэкап повреждённым файлом.
            try {
                write(configFile, fromBackup);
            } catch (IOException e) {
                throw new FatalConfigException("Не удалось восстановить конфиг из бэкапа: " + e.getMessage());
            }
            return fromBackup;
        }

        // §5.2 п.3: вопрос о сбросе к значениям по умолчанию.
        if (prompts.askResetToDefaults()) {
            Config defaults = Config.defaults();
            try {
                write(configFile, defaults); // без предварительного бэкапа повреждённого файла
            } catch (IOException e) {
                throw new FatalConfigException("Не удалось перезаписать конфиг значениями по умолчанию: " + e.getMessage());
            }
            return defaults;
        }

        // Пользователь отказался — аварийное завершение, файл не трогаем.
        throw new FatalConfigException("Конфигурация повреждена; восстановление и сброс отклонены пользователем.");
    }

    /**
     * Сохранение при каждом изменении (§3.9): сначала бэкап текущего файла,
     * затем запись новых настроек.
     */
    public void save(Config config) throws IOException {
        if (configFile.exists()) {
            Files.copy(configFile.toPath(), backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        write(configFile, config);
    }

    private void saveQuietly(Config config) {
        try {
            save(config);
        } catch (IOException e) {
            System.err.println("ClickerCat: не удалось записать конфиг: " + e.getMessage());
        }
    }

    // ---- чтение/запись YAML -------------------------------------------------

    private Config read(File file) throws InvalidConfigException {
        Object root;
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            root = yaml.load(r);
        } catch (IOException e) {
            throw new InvalidConfigException("Не удалось прочитать файл: " + file, e);
        } catch (RuntimeException e) {
            throw new InvalidConfigException("Ошибка разбора YAML: " + e.getMessage(), e);
        }
        return parse(root);
    }

    private void write(File file, Config config) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            yaml.dump(toMap(config), w);
        }
    }

    private static Map<String, Object> toMap(Config c) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("enabled", c.enabled);
        root.put("activeProfile", c.activeProfile);
        List<Object> profiles = new ArrayList<>();
        for (Profile p : c.profiles) {
            profiles.add(profileToMap(p));
        }
        root.put("profiles", profiles);
        return root;
    }

    private static Map<String, Object> profileToMap(Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name);
        m.put("hotkey", bindingToMap(p.hotkey));
        m.put("emergencyStop", bindingToMap(p.emergencyStop));
        m.put("mode", p.mode.name());
        m.put("delayMs", p.delayMs);
        List<Object> keys = new ArrayList<>();
        for (KeyBinding kb : p.keys) {
            keys.add(bindingToMap(kb));
        }
        m.put("keys", keys);
        m.put("alwaysOnTop", p.alwaysOnTop);
        return m;
    }

    private static Map<String, Object> bindingToMap(KeyBinding kb) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", kb.type.name());
        m.put("code", kb.code);
        return m;
    }

    // ---- разбор и валидация -------------------------------------------------

    private static Config parse(Object root) throws InvalidConfigException {
        if (!(root instanceof Map<?, ?> map)) {
            throw new InvalidConfigException("Корень конфига должен быть отображением (mapping).");
        }
        Config c = new Config();
        c.enabled = parseEnabled(map.get("enabled"));

        // Старый формат (до профилей): настройки лежат прямо в корне — заворачиваем
        // их в единственный профиль. Файл перепишется в новом формате при первом сохранении.
        if (map.get("profiles") == null) {
            c.profiles.add(parseProfile(map, Profile.DEFAULT_NAME, "конфиг"));
            c.activeProfile = Profile.DEFAULT_NAME;
            return c;
        }

        if (!(map.get("profiles") instanceof List<?> list) || list.isEmpty()) {
            throw new InvalidConfigException("Поле 'profiles' должно быть непустым списком.");
        }
        int i = 0;
        for (Object item : list) {
            String where = "profiles[" + i + "]";
            if (!(item instanceof Map<?, ?> pm)) {
                throw new InvalidConfigException("Элемент '" + where + "' должен быть отображением (mapping).");
            }
            String name = parseName(pm.get("name"), where);
            if (c.hasProfile(name)) {
                throw new InvalidConfigException("Имена профилей должны быть уникальными; повтор: " + name);
            }
            c.profiles.add(parseProfile(pm, name, where));
            i++;
        }

        // Имя может отсутствовать или не совпадать ни с одним профилем (ручная правка файла) —
        // это не ошибка: Config.active() в таком случае вернёт первый профиль.
        Object active = map.get("activeProfile");
        c.activeProfile = active instanceof String s ? s : c.profiles.get(0).name;
        return c;
    }

    private static Profile parseProfile(Map<?, ?> map, String name, String where)
            throws InvalidConfigException {
        Profile p = new Profile();
        p.name = name;
        p.hotkey = parseBinding(map.get("hotkey"), where + ".hotkey");
        p.emergencyStop = parseBinding(map.get("emergencyStop"), where + ".emergencyStop");
        p.mode = parseMode(map.get("mode"), where);
        p.delayMs = parseDelay(map.get("delayMs"), where);
        p.keys = parseKeys(map.get("keys"), where);
        p.alwaysOnTop = parseAlwaysOnTop(map.get("alwaysOnTop"));
        return p;
    }

    private static String parseName(Object value, String where) throws InvalidConfigException {
        if (!(value instanceof String s) || s.isBlank()) {
            throw new InvalidConfigException("Поле '" + where + ".name' должно быть непустой строкой.");
        }
        return s;
    }

    private static KeyBinding parseBinding(Object value, String field) throws InvalidConfigException {
        if (!(value instanceof Map<?, ?> m)) {
            throw new InvalidConfigException("Поле '" + field + "' отсутствует или имеет неверный формат.");
        }
        Object typeRaw = m.get("type");
        Object codeRaw = m.get("code");
        if (!(typeRaw instanceof String typeStr) || !(codeRaw instanceof String codeStr) || codeStr.isBlank()) {
            throw new InvalidConfigException("Поле '" + field + "' должно содержать строковые 'type' и 'code'.");
        }
        InputType type;
        try {
            type = InputType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigException("Недопустимый type в '" + field + "': " + typeStr);
        }
        return new KeyBinding(type, codeStr);
    }

    private static Mode parseMode(Object value, String where) throws InvalidConfigException {
        if (!(value instanceof String s)) {
            throw new InvalidConfigException("Поле '" + where + ".mode' отсутствует или не является строкой.");
        }
        try {
            return Mode.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigException("Недопустимый mode в '" + where + "': " + s);
        }
    }

    private static int parseDelay(Object value, String where) throws InvalidConfigException {
        if (!(value instanceof Integer delay)) {
            throw new InvalidConfigException("Поле '" + where + ".delayMs' отсутствует или не является целым числом.");
        }
        if (delay < Config.MIN_DELAY_MS || delay > Config.MAX_DELAY_MS) {
            throw new InvalidConfigException("delayMs в '" + where + "' вне диапазона " + Config.MIN_DELAY_MS
                    + ".." + Config.MAX_DELAY_MS + ": " + delay);
        }
        return delay;
    }

    /** Необязательное поле (добавлено позже): отсутствие трактуется как false для совместимости. */
    private static boolean parseAlwaysOnTop(Object value) {
        return value instanceof Boolean b && b;
    }

    /**
     * Необязательное поле (добавлено позже): отсутствие трактуется как {@code true},
     * чтобы старые конфиги открывались с включённым кликером.
     */
    private static boolean parseEnabled(Object value) {
        return !(value instanceof Boolean b) || b;
    }

    private static List<KeyBinding> parseKeys(Object value, String where) throws InvalidConfigException {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new InvalidConfigException("Поле '" + where + ".keys' должно быть непустым списком.");
        }
        List<KeyBinding> keys = new ArrayList<>();
        int i = 0;
        for (Object item : list) {
            keys.add(parseBinding(item, where + ".keys[" + i + "]"));
            i++;
        }
        return keys;
    }
}
