package cat.clicker.ui;

import cat.clicker.config.Config;
import cat.clicker.config.Config.KeyBinding;
import cat.clicker.config.Config.Mode;
import cat.clicker.config.ConfigStore;
import cat.clicker.config.Profile;
import cat.clicker.core.Clicker;
import cat.clicker.core.HotkeyService;
import cat.clicker.input.KeyMapper;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Главное окно Swing (§4). Долгие операции (клики, перехват) выполняются вне EDT;
 * обновление UI — только через {@link SwingUtilities#invokeLater}.
 */
public class MainWindow extends JFrame implements HotkeyService.Listener {

    private enum Role {TRIGGER, EMERGENCY, LIST}

    private final Config config;
    private final ConfigStore store;
    private final HotkeyService hotkeys;
    private final Clicker clicker;

    private final JComboBox<Profile> profileCombo = new JComboBox<>();
    private final JButton newProfileBtn = new JButton("+");
    private final JButton renameProfileBtn = new JButton("✏️");
    private final JButton deleteProfileBtn = new JButton("−");
    private final JLabel hotkeyLabel = new JLabel();
    private final JLabel emergencyLabel = new JLabel();
    private final JButton assignHotkeyBtn = new JButton("✏️");
    private final JButton assignEmergencyBtn = new JButton("✏️");
    private final JRadioButton holdRadio = new JRadioButton("Удержание");
    private final JRadioButton toggleRadio = new JRadioButton("Переключение");
    private final JTextField delayField = new JTextField(6);
    private final DefaultListModel<KeyBinding> keysModel = new DefaultListModel<>();
    private final JList<KeyBinding> keysList = new JList<>(keysModel);
    private final JButton addKeyBtn = new JButton("+");
    private final JButton removeKeyBtn = new JButton("−");
    private final JLabel statusLabel = new JLabel();
    private final JButton enableBtn = new JButton("Вкл/Выкл");
    private final JCheckBox alwaysOnTopCheck = new JCheckBox("Поверх всех окон");

    /** Значок в системном трее; null, если трей не поддерживается ОС. */
    private TrayIcon trayIcon;
    private MenuItem trayWindowItem;
    private MenuItem trayClickerItem;
    private Image iconActive;
    private Image iconInactive;

    /** Последнее валидное значение задержки — для отката неверного ввода (§3.6). */
    private int lastValidDelay;

    /** Не реагировать на выбор в комбобоксе, пока он перезаполняется из конфига. */
    private boolean suppressProfileEvents;

    /**
     * Копия активного профиля для чтения с потока хука без гонок: там читаются
     * клавиши, режим и задержка, а профиль может смениться прямо во время клика.
     * Обновляется на EDT при каждом изменении (заменой ссылки).
     */
    private volatile Profile snapshot;

    public MainWindow(Config config, ConfigStore store, HotkeyService hotkeys, Clicker clicker) {
        super("ClickerCat");
        this.config = config;
        this.store = store;
        this.hotkeys = hotkeys;
        this.clicker = clicker;
        this.lastValidDelay = config.active().delayMs;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        buildUi();
        setupTray();
        loadFromConfig();

        hotkeys.setListener(this);
        hotkeys.setHotkey(profile().hotkey);
        hotkeys.setEmergencyStop(profile().emergencyStop);
        hotkeys.setEnabled(config.enabled);

        if (!hotkeys.isRegistered()) {
            assignHotkeyBtn.setEnabled(false);
            assignEmergencyBtn.setEnabled(false);
            addKeyBtn.setEnabled(false);
            statusLabel.setText("Глобальный хук недоступен (см. сообщение)");
        }

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    // ---- построение UI ------------------------------------------------------

    private void buildUi() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Профиль
        profileCombo.setRenderer(new ProfileRenderer());
        compact(newProfileBtn, "Создать профиль");
        compact(deleteProfileBtn, "Удалить текущий профиль");
        compact(renameProfileBtn, "Переименовать профиль");
        compact(assignHotkeyBtn, "Назначить горячую клавишу");
        compact(assignEmergencyBtn, "Назначить клавишу аварийной остановки");
        compact(addKeyBtn, "Добавить клавишу");
        compact(removeKeyBtn, "Удалить выбранную клавишу (Delete)");

        c.gridx = 0;
        c.gridy = row;
        root.add(new JLabel("Профиль:"), c);
        c.gridx = 1;
        root.add(profileCombo, c);
        c.gridx = 2;
        addButtons(root, c, newProfileBtn, deleteProfileBtn, renameProfileBtn);
        row++;

        // Горячая клавиша
        addRow(root, c, row++, new JLabel("Горячая клавиша:"), boxed(hotkeyLabel), assignHotkeyBtn);
        // Аварийная остановка
        addRow(root, c, row++, new JLabel("Аварийная остановка:"), boxed(emergencyLabel), assignEmergencyBtn);

        // Режим
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(holdRadio);
        modeGroup.add(toggleRadio);
        JPanel modePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        modePanel.add(holdRadio);
        modePanel.add(toggleRadio);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        root.add(new JLabel("Режим:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        root.add(modePanel, c);
        c.gridwidth = 1;
        row++;

        // Задержка
        JPanel delayPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        delayPanel.add(delayField);
        delayPanel.add(new JLabel("мс (0–10000)"));
        c.gridx = 0;
        c.gridy = row;
        root.add(new JLabel("Задержка между итерациями:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        root.add(delayPanel, c);
        c.gridwidth = 1;
        row++;

        // Поверх всех окон
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        root.add(alwaysOnTopCheck, c);
        c.gridwidth = 1;
        row++;

        // Список клавиш
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        root.add(new JLabel("Нажимать клавиши:"), c);
        c.gridwidth = 1;
        row++;

        keysList.setVisibleRowCount(5);
        keysList.setCellRenderer(new BindingRenderer());
        JScrollPane scroll = new JScrollPane(keysList);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        root.add(scroll, c);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 2;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        addButtons(root, c, addKeyBtn, removeKeyBtn);
        c.anchor = GridBagConstraints.WEST;
        row++;

        // Статус + Вкл/Выкл
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        root.add(statusLabel, c);
        c.gridx = 2;
        c.gridwidth = 1;
        addButtons(root, c, enableBtn);

        setContentPane(root);

        wireActions();
    }

    private void wireActions() {
        profileCombo.addActionListener(e -> {
            if (suppressProfileEvents) {
                return;
            }
            Profile selected = (Profile) profileCombo.getSelectedItem();
            if (selected != null && !selected.name.equals(config.activeProfile)) {
                switchProfile(selected);
            }
        });
        newProfileBtn.addActionListener(e -> createProfile());
        renameProfileBtn.addActionListener(e -> renameProfile());
        deleteProfileBtn.addActionListener(e -> deleteProfile());

        assignHotkeyBtn.addActionListener(e -> beginCapture(Role.TRIGGER));
        assignEmergencyBtn.addActionListener(e -> beginCapture(Role.EMERGENCY));
        addKeyBtn.addActionListener(e -> beginCapture(Role.LIST));
        removeKeyBtn.addActionListener(e -> removeSelectedKey());

        // Delete на выделенном элементе списка — то же, что кнопка «Удалить».
        keysList.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeKey");
        keysList.getActionMap().put("removeKey", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (removeKeyBtn.isEnabled()) {
                    removeSelectedKey();
                }
            }
        });

        holdRadio.addActionListener(e -> {
            profile().mode = Mode.HOLD;
            persist();
        });
        toggleRadio.addActionListener(e -> {
            profile().mode = Mode.TOGGLE;
            persist();
        });

        delayField.addActionListener(e -> commitDelay());
        delayField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitDelay();
            }
        });

        enableBtn.addActionListener(e -> toggleEnabled());

        alwaysOnTopCheck.addActionListener(e -> {
            boolean on = alwaysOnTopCheck.isSelected();
            setAlwaysOnTop(on);
            profile().alwaysOnTop = on;
            persist();
        });
    }

    // ---- профили ------------------------------------------------------------

    /** Активный профиль: все настройки, кроме главного выключателя, живут в нём. */
    private Profile profile() {
        return config.active();
    }

    /**
     * Сделать профиль активным. Текущее кликанье при этом останавливается: новый профиль
     * может задавать другие клавиши, задержку и режим.
     */
    private void switchProfile(Profile target) {
        clicker.stop();
        config.activeProfile = target.name;
        hotkeys.setHotkey(target.hotkey);
        hotkeys.setEmergencyStop(target.emergencyStop);
        lastValidDelay = target.delayMs;
        loadFromConfig();
        persist();
    }

    private void createProfile() {
        String name = askProfileName("Имя нового профиля:", "");
        if (name == null) {
            return;
        }
        Profile created = Profile.defaults(name);
        config.profiles.add(created);
        switchProfile(created);
    }

    private void renameProfile() {
        Profile current = profile();
        String name = askProfileName("Новое имя профиля:", current.name);
        if (name == null) {
            return;
        }
        current.name = name;
        config.activeProfile = name;
        loadFromConfig();
        persist();
    }

    private void deleteProfile() {
        if (config.profiles.size() <= 1) {
            JOptionPane.showMessageDialog(this,
                    "Это последний профиль — должен остаться хотя бы один.",
                    "Нельзя удалить", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Profile current = profile();
        int answer = JOptionPane.showConfirmDialog(this,
                "Удалить профиль «" + current.name + "»?",
                "Удаление профиля", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        config.profiles.remove(current);
        switchProfile(config.profiles.get(0));
    }

    /**
     * Запросить имя профиля с проверкой (§3.4-стиль: непустое и уникальное).
     *
     * @return имя либо null, если пользователь отменил ввод или имя не прошло проверку.
     */
    private String askProfileName(String message, String initial) {
        Object input = JOptionPane.showInputDialog(this, message, "Профиль",
                JOptionPane.QUESTION_MESSAGE, null, null, initial);
        if (input == null) {
            return null;
        }
        String name = input.toString().trim();
        if (name.equals(initial)) {
            return null; // имя не изменилось — делать нечего
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Имя профиля не может быть пустым.",
                    "Неверное имя", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (config.hasProfile(name)) {
            JOptionPane.showMessageDialog(this, "Профиль с именем «" + name + "» уже существует.",
                    "Неверное имя", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return name;
    }

    /** Переключить главный выключатель механизма (кнопка/трей) и обновить UI. */
    private void toggleEnabled() {
        applyEnabled(!config.enabled);
    }

    /**
     * Включить/выключить весь механизм автокликера. При выключении текущее кликанье
     * останавливается, а горячие клавиши перестают срабатывать — приложение «на паузе».
     */
    private void applyEnabled(boolean on) {
        config.enabled = on;
        hotkeys.setEnabled(on);
        if (!on) {
            clicker.stop();
        }
        persist();
        updateStatus();
    }

    /** Кнопка-символ («+», «−», «✏️»): узкая, с подсказкой вместо надписи. */
    private static void compact(JButton button, String tooltip) {
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setPreferredSize(new Dimension(34, button.getPreferredSize().height));
    }

    /**
     * Положить кнопки в ячейку, не давая GridBagLayout растянуть их по её ширине:
     * тянется только панель, кнопки внутри сохраняют свой размер.
     */
    private static void addButtons(JPanel root, GridBagConstraints c, JButton... buttons) {
        JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        for (JButton b : buttons) {
            panel.add(b);
        }
        root.add(panel, c);
    }

    private static JComponent boxed(JLabel label) {
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        return label;
    }

    private void addRow(JPanel root, GridBagConstraints c, int row,
                        JComponent label, JComponent value, JButton button) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        root.add(label, c);
        c.gridx = 1;
        root.add(value, c);
        c.gridx = 2;
        addButtons(root, c, button);
    }

    private void loadFromConfig() {
        Profile p = profile();

        suppressProfileEvents = true;
        try {
            profileCombo.setModel(new DefaultComboBoxModel<>(config.profiles.toArray(new Profile[0])));
            profileCombo.setSelectedItem(p);
        } finally {
            suppressProfileEvents = false;
        }

        hotkeyLabel.setText(KeyMapper.displayName(p.hotkey));
        emergencyLabel.setText(KeyMapper.displayName(p.emergencyStop));
        holdRadio.setSelected(p.mode == Mode.HOLD);
        toggleRadio.setSelected(p.mode == Mode.TOGGLE);
        delayField.setText(Integer.toString(p.delayMs));
        alwaysOnTopCheck.setSelected(p.alwaysOnTop);
        setAlwaysOnTop(p.alwaysOnTop);
        keysModel.clear();
        for (KeyBinding kb : p.keys) {
            keysModel.addElement(kb);
        }
        refreshSnapshot();
        updateStatus();
    }

    // ---- захват назначений --------------------------------------------------

    private void beginCapture(Role role) {
        if (!hotkeys.isRegistered()) {
            return;
        }
        setControlsEnabled(false);
        JLabel target = role == Role.TRIGGER ? hotkeyLabel : role == Role.EMERGENCY ? emergencyLabel : null;
        String previous = target != null ? target.getText() : null;
        if (target != null) {
            target.setText("Нажмите клавишу…");
        } else {
            statusLabel.setText("Нажмите клавишу для добавления…");
        }

        Consumer<KeyBinding> onCaptured = binding ->
                SwingUtilities.invokeLater(() -> finishCapture(role, binding, target, previous));
        hotkeys.captureNext(onCaptured);
    }

    private void finishCapture(Role role, KeyBinding binding, JLabel target, String previous) {
        setControlsEnabled(true);
        updateStatus();

        String conflict = conflictMessage(binding, role);
        if (conflict != null) {
            if (target != null && previous != null) {
                target.setText(previous);
            }
            JOptionPane.showMessageDialog(this, conflict, "Клавиша занята", JOptionPane.WARNING_MESSAGE);
            return;
        }

        switch (role) {
            case TRIGGER -> {
                profile().hotkey = binding;
                hotkeys.setHotkey(binding);
                hotkeyLabel.setText(KeyMapper.displayName(binding));
            }
            case EMERGENCY -> {
                profile().emergencyStop = binding;
                hotkeys.setEmergencyStop(binding);
                emergencyLabel.setText(KeyMapper.displayName(binding));
            }
            case LIST -> {
                profile().keys.add(binding);
                keysModel.addElement(binding);
            }
        }
        persist();
    }

    /**
     * Проверка взаимоисключения ролей (§3.8) — в пределах активного профиля: профили
     * независимы, одна и та же клавиша может быть триггером в одном и нажатием в другом.
     *
     * @return сообщение об ошибке или null.
     */
    private String conflictMessage(KeyBinding candidate, Role assigning) {
        Profile p = profile();
        if (assigning != Role.TRIGGER && candidate.equals(p.hotkey)) {
            return "Эта клавиша уже назначена как горячая клавиша срабатывания.";
        }
        if (assigning != Role.EMERGENCY && candidate.equals(p.emergencyStop)) {
            return "Эта клавиша уже назначена как аварийная остановка.";
        }
        if (p.keys.contains(candidate)) {
            return "Эта клавиша уже есть в списке нажатий.";
        }
        return null;
    }

    private void removeSelectedKey() {
        int idx = keysList.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        if (keysModel.size() <= 1) {
            JOptionPane.showMessageDialog(this,
                    "Список не может быть пустым — оставьте хотя бы один элемент.",
                    "Нельзя удалить", JOptionPane.WARNING_MESSAGE);
            return;
        }
        KeyBinding removed = keysModel.remove(idx);
        profile().keys.remove(removed);
        persist();
    }

    // ---- задержка -----------------------------------------------------------

    private void commitDelay() {
        String text = delayField.getText().trim();
        try {
            int value = Integer.parseInt(text);
            if (value < Config.MIN_DELAY_MS || value > Config.MAX_DELAY_MS) {
                throw new NumberFormatException("вне диапазона");
            }
            if (value != profile().delayMs) {
                profile().delayMs = value;
                lastValidDelay = value;
                persist();
            }
        } catch (NumberFormatException ex) {
            // откат к последнему валидному значению
            delayField.setText(Integer.toString(lastValidDelay));
            profile().delayMs = lastValidDelay;
        }
    }

    // ---- сохранение / состояние --------------------------------------------

    /** Сохранить конфиг; вызывается при каждом изменении (§3.9), заодно обновляет снимок. */
    private void persist() {
        refreshSnapshot();
        try {
            store.save(config);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Не удалось сохранить настройки: " + e.getMessage(),
                    "Ошибка сохранения", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshSnapshot() {
        snapshot = profile().copy();
    }

    private void setControlsEnabled(boolean enabled) {
        profileCombo.setEnabled(enabled);
        newProfileBtn.setEnabled(enabled);
        renameProfileBtn.setEnabled(enabled);
        deleteProfileBtn.setEnabled(enabled);
        assignHotkeyBtn.setEnabled(enabled);
        assignEmergencyBtn.setEnabled(enabled);
        addKeyBtn.setEnabled(enabled);
        removeKeyBtn.setEnabled(enabled);
        holdRadio.setEnabled(enabled);
        toggleRadio.setEnabled(enabled);
        delayField.setEnabled(enabled);
        enableBtn.setEnabled(enabled);
    }

    private void updateStatus() {
        boolean on = config.enabled;
        boolean running = clicker.isRunning();
        String state = !on ? "Отключён" : running ? "Активен" : "Ожидание";
        statusLabel.setText("Статус: " + state);
        enableBtn.setText(on ? "Выкл" : "Вкл");
        updateTray(on);
    }

    // ---- системный трей -----------------------------------------------------

    /** Создать значок в трее с меню; молча пропускается, если трей не поддерживается. */
    private void setupTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        Dimension size = SystemTray.getSystemTray().getTrayIconSize();
        iconActive = makeIcon(size.width, size.height, true);
        iconInactive = makeIcon(size.width, size.height, false);

        PopupMenu menu = new PopupMenu();
        trayWindowItem = new MenuItem("Скрыть окно");
        trayWindowItem.addActionListener(e -> SwingUtilities.invokeLater(this::toggleWindow));
        trayClickerItem = new MenuItem("Выключить");
        trayClickerItem.addActionListener(e -> SwingUtilities.invokeLater(this::toggleEnabled));
        MenuItem exitItem = new MenuItem("Выход");
        exitItem.addActionListener(e -> System.exit(0));
        menu.add(trayWindowItem);
        menu.add(trayClickerItem);
        menu.addSeparator();
        menu.add(exitItem);

        trayIcon = new TrayIcon(iconInactive, "ClickerCat", menu);
        trayIcon.setImageAutoSize(true);
        // Двойной клик по значку — показать/поднять окно.
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(this::showWindow));

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
            return;
        }

        // С треем закрытие окна спрашивает: свернуть в трей или выйти (§4).
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmClose();
            }
        });
    }

    private void updateTray(boolean on) {
        if (trayIcon == null) {
            return;
        }
        trayIcon.setImage(on ? iconActive : iconInactive);
        trayIcon.setToolTip("ClickerCat — " + (on ? "включён" : "отключён"));
        if (trayClickerItem != null) {
            trayClickerItem.setLabel(on ? "Выключить" : "Включить");
        }
    }

    private void toggleWindow() {
        if (isVisible()) {
            hideToTray();
        } else {
            showWindow();
        }
    }

    private void showWindow() {
        setVisible(true);
        setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        toFront();
        requestFocus();
        if (trayWindowItem != null) {
            trayWindowItem.setLabel("Скрыть окно");
        }
    }

    private void hideToTray() {
        setVisible(false);
        if (trayWindowItem != null) {
            trayWindowItem.setLabel("Показать окно");
        }
    }

    /** При закрытии окна спросить: свернуть в трей, выйти или отменить. */
    private void confirmClose() {
        Object[] options = {"Свернуть в трей", "Выйти", "Отмена"};
        int choice = JOptionPane.showOptionDialog(this,
                "Свернуть приложение в трей или полностью закрыть его?",
                "Закрытие ClickerCat",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        switch (choice) {
            case JOptionPane.YES_OPTION -> hideToTray();
            case JOptionPane.NO_OPTION -> System.exit(0);
            default -> { /* Отмена / закрытие диалога — ничего не делаем */ }
        }
    }

    /** Нарисовать простой значок-кота: зелёный — активен, серый — остановлен. */
    private static Image makeIcon(int w, int h, boolean active) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color body = active ? new Color(0x3C, 0xB3, 0x71) : new Color(0x9E, 0x9E, 0x9E);
        g.setColor(body);

        // Уши — два треугольника сверху.
        int earH = Math.round(h * 0.32f);
        int earW = Math.round(w * 0.30f);
        int top = Math.round(h * 0.12f);
        g.fillPolygon(new int[]{w / 5, w / 5 + earW, w / 5}, new int[]{top + earH, top + earH, top}, 3);
        g.fillPolygon(new int[]{w - w / 5, w - w / 5 - earW, w - w / 5},
                new int[]{top + earH, top + earH, top}, 3);

        // Голова — круг.
        int d = Math.round(Math.min(w, h) * 0.66f);
        int cx = (w - d) / 2;
        int cy = h - d - Math.round(h * 0.06f);
        g.fillOval(cx, cy, d, d);

        // Глаза — две тёмные точки.
        g.setColor(new Color(0x20, 0x20, 0x20));
        int eye = Math.max(1, Math.round(d * 0.14f));
        int eyeY = cy + Math.round(d * 0.42f);
        g.fillOval(cx + Math.round(d * 0.28f) - eye / 2, eyeY, eye, eye);
        g.fillOval(cx + Math.round(d * 0.72f) - eye / 2, eyeY, eye, eye);

        g.dispose();
        return img;
    }

    // ---- HotkeyService.Listener (вызовы НЕ на EDT) --------------------------

    @Override
    public void onTriggerPressed() {
        Profile p = snapshot;
        if (p.mode == Mode.HOLD) {
            clicker.start(p.keys, p.delayMs);
        } else {
            if (clicker.isRunning()) {
                clicker.stop();
            } else {
                clicker.start(p.keys, p.delayMs);
            }
        }
        SwingUtilities.invokeLater(this::updateStatus);
    }

    @Override
    public void onTriggerReleased() {
        if (snapshot.mode == Mode.HOLD) {
            clicker.stop();
            SwingUtilities.invokeLater(this::updateStatus);
        }
    }

    @Override
    public void onEmergencyStop() {
        clicker.stop();
        SwingUtilities.invokeLater(this::updateStatus);
    }

    // ---- рендер списка ------------------------------------------------------

    private static final class BindingRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof KeyBinding kb) {
                setText(KeyMapper.displayName(kb));
            }
            return this;
        }
    }

    private static final class ProfileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Profile p) {
                setText(p.name);
            }
            return this;
        }
    }
}
