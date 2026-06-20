package cat.clicker.ui;

import cat.clicker.config.Config;
import cat.clicker.config.Config.KeyBinding;
import cat.clicker.config.Config.Mode;
import cat.clicker.config.ConfigStore;
import cat.clicker.core.Clicker;
import cat.clicker.core.HotkeyService;
import cat.clicker.input.KeyMapper;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.List;
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

    private final JLabel hotkeyLabel = new JLabel();
    private final JLabel emergencyLabel = new JLabel();
    private final JButton assignHotkeyBtn = new JButton("Назначить");
    private final JButton assignEmergencyBtn = new JButton("Назначить");
    private final JRadioButton holdRadio = new JRadioButton("Удержание");
    private final JRadioButton toggleRadio = new JRadioButton("Переключение");
    private final JTextField delayField = new JTextField(6);
    private final DefaultListModel<KeyBinding> keysModel = new DefaultListModel<>();
    private final JList<KeyBinding> keysList = new JList<>(keysModel);
    private final JButton addKeyBtn = new JButton("Добавить");
    private final JButton removeKeyBtn = new JButton("Удалить");
    private final JLabel statusLabel = new JLabel();
    private final JButton startStopBtn = new JButton("Старт/Стоп");

    /** Последнее валидное значение задержки — для отката неверного ввода (§3.6). */
    private int lastValidDelay;

    /**
     * Неизменяемый снимок списка клавиш для чтения с потока хука без гонок.
     * Обновляется на EDT при каждом изменении (заменой ссылки).
     */
    private volatile List<KeyBinding> keysSnapshot = List.of();

    public MainWindow(Config config, ConfigStore store, HotkeyService hotkeys, Clicker clicker) {
        super("ClickerCat");
        this.config = config;
        this.store = store;
        this.hotkeys = hotkeys;
        this.clicker = clicker;
        this.lastValidDelay = config.delayMs;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        buildUi();
        loadFromConfig();

        hotkeys.setListener(this);
        hotkeys.setHotkey(config.hotkey);
        hotkeys.setEmergencyStop(config.emergencyStop);

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
        c.gridheight = 2;
        c.fill = GridBagConstraints.BOTH;
        root.add(scroll, c);
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 2;
        c.gridy = row;
        c.gridwidth = 1;
        root.add(addKeyBtn, c);
        c.gridx = 2;
        c.gridy = row + 1;
        root.add(removeKeyBtn, c);
        row += 2;

        // Статус + Старт/Стоп
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        root.add(statusLabel, c);
        c.gridx = 2;
        c.gridwidth = 1;
        root.add(startStopBtn, c);

        setContentPane(root);

        wireActions();
    }

    private void wireActions() {
        assignHotkeyBtn.addActionListener(e -> beginCapture(Role.TRIGGER));
        assignEmergencyBtn.addActionListener(e -> beginCapture(Role.EMERGENCY));
        addKeyBtn.addActionListener(e -> beginCapture(Role.LIST));
        removeKeyBtn.addActionListener(e -> removeSelectedKey());

        holdRadio.addActionListener(e -> {
            config.mode = Mode.HOLD;
            persist();
        });
        toggleRadio.addActionListener(e -> {
            config.mode = Mode.TOGGLE;
            persist();
        });

        delayField.addActionListener(e -> commitDelay());
        delayField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitDelay();
            }
        });

        startStopBtn.addActionListener(e -> {
            if (clicker.isRunning()) {
                clicker.stop();
            } else {
                clicker.start(currentKeys(), config.delayMs);
            }
            updateStatus();
        });
    }

    private static JComponent boxed(JLabel label) {
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        return label;
    }

    private void addRow(JPanel root, GridBagConstraints c, int row,
                        JComponent label, JComponent value, JComponent button) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        root.add(label, c);
        c.gridx = 1;
        root.add(value, c);
        c.gridx = 2;
        root.add(button, c);
    }

    private void loadFromConfig() {
        hotkeyLabel.setText(KeyMapper.displayName(config.hotkey));
        emergencyLabel.setText(KeyMapper.displayName(config.emergencyStop));
        holdRadio.setSelected(config.mode == Mode.HOLD);
        toggleRadio.setSelected(config.mode == Mode.TOGGLE);
        delayField.setText(Integer.toString(config.delayMs));
        keysModel.clear();
        for (KeyBinding kb : config.keys) {
            keysModel.addElement(kb);
        }
        refreshKeysSnapshot();
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
                config.hotkey = binding;
                hotkeys.setHotkey(binding);
                hotkeyLabel.setText(KeyMapper.displayName(binding));
            }
            case EMERGENCY -> {
                config.emergencyStop = binding;
                hotkeys.setEmergencyStop(binding);
                emergencyLabel.setText(KeyMapper.displayName(binding));
            }
            case LIST -> {
                config.keys.add(binding);
                keysModel.addElement(binding);
                refreshKeysSnapshot();
            }
        }
        persist();
    }

    /** Проверка взаимоисключения ролей (§3.8). @return сообщение об ошибке или null. */
    private String conflictMessage(KeyBinding candidate, Role assigning) {
        if (assigning != Role.TRIGGER && candidate.equals(config.hotkey)) {
            return "Эта клавиша уже назначена как горячая клавиша срабатывания.";
        }
        if (assigning != Role.EMERGENCY && candidate.equals(config.emergencyStop)) {
            return "Эта клавиша уже назначена как аварийная остановка.";
        }
        if (config.keys.contains(candidate)) {
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
        config.keys.remove(removed);
        refreshKeysSnapshot();
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
            if (value != config.delayMs) {
                config.delayMs = value;
                lastValidDelay = value;
                persist();
            }
        } catch (NumberFormatException ex) {
            // откат к последнему валидному значению
            delayField.setText(Integer.toString(lastValidDelay));
            config.delayMs = lastValidDelay;
        }
    }

    // ---- сохранение / состояние --------------------------------------------

    private void persist() {
        try {
            store.save(config);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Не удалось сохранить настройки: " + e.getMessage(),
                    "Ошибка сохранения", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshKeysSnapshot() {
        keysSnapshot = List.copyOf(config.keys);
    }

    private List<KeyBinding> currentKeys() {
        return keysSnapshot;
    }

    private void setControlsEnabled(boolean enabled) {
        assignHotkeyBtn.setEnabled(enabled);
        assignEmergencyBtn.setEnabled(enabled);
        addKeyBtn.setEnabled(enabled);
        removeKeyBtn.setEnabled(enabled);
        holdRadio.setEnabled(enabled);
        toggleRadio.setEnabled(enabled);
        delayField.setEnabled(enabled);
        startStopBtn.setEnabled(enabled);
    }

    private void updateStatus() {
        boolean running = clicker.isRunning();
        statusLabel.setText("Статус: " + (running ? "Активен" : "Остановлен"));
        startStopBtn.setText(running ? "Стоп" : "Старт");
    }

    // ---- HotkeyService.Listener (вызовы НЕ на EDT) --------------------------

    @Override
    public void onTriggerPressed() {
        if (config.mode == Mode.HOLD) {
            clicker.start(currentKeys(), config.delayMs);
        } else {
            if (clicker.isRunning()) {
                clicker.stop();
            } else {
                clicker.start(currentKeys(), config.delayMs);
            }
        }
        SwingUtilities.invokeLater(this::updateStatus);
    }

    @Override
    public void onTriggerReleased() {
        if (config.mode == Mode.HOLD) {
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
}
