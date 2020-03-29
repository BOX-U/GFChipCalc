package main.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import main.App;
import main.http.ResearchConnection;
import main.json.JsonParser;
import main.puzzle.Board;
import main.puzzle.Chip;
import main.puzzle.Stat;
import main.puzzle.Tag;
import main.puzzle.assembly.Assembler;
import main.puzzle.assembly.Progress;
import main.puzzle.preset.PuzzlePreset;
import main.resource.Language;
import main.resource.Resources;
import main.setting.BoardSetting;
import main.setting.Setting;
import main.ui.dialog.AppSettingDialog;
import main.ui.dialog.ApplyDialog;
import main.ui.dialog.CalcSettingDialog;
import main.ui.dialog.FilterDialog;
import main.ui.dialog.ImageDialog;
import main.ui.dialog.JsonFilterDialog;
import main.ui.dialog.ProxyDialog;
import main.ui.dialog.StatDialog;
import main.ui.dialog.TagDialog;
import main.ui.help.HelpDialog;
import main.ui.renderer.ChipListCellRenderer;
import main.ui.renderer.CombListCellRenderer;
import main.ui.renderer.InvListCellRenderer;
import main.ui.shortcut.ShortcutKeyAdapter;
import main.ui.tip.TipMouseListener;
import main.ui.transfer.InvListTransferHandler;
import main.util.Fn;
import main.util.IO;

/**
 *
 * @author Bunnyspa
 */
public class MainFrame extends JFrame {

    /* STATIC */
    // UI
    private static final int BORDERSIZE = 3;

    // Chip Stat
    private static final int FOCUSED_NONE = -1;
    private static final int FOCUSED_DMG = 0;
    // private static final int FOCUSED_BRK = 1;
    // private static final int FOCUSED_HIT = 2;
    // private static final int FOCUSED_RLD = 3;
    private static final int INPUT_BUFFER_SIZE = 3;

    // Sort
    private static final int SORT_NONE = 0;
    private static final int SORT_SIZE = 1;
    private static final int SORT_LEVEL = 2;
    private static final int SORT_STAR = 3;
    private static final int SORT_DMG = 4;
    private static final int SORT_BRK = 5;
    private static final int SORT_HIT = 6;
    private static final int SORT_RLD = 7;

    // Calculator
    private static final int SIZE_DONETIME = 100;

    // Setting
    private static final boolean ASCENDING = Setting.ASCENDING;
    private static final boolean DESCENDING = Setting.DESCENDING;
    private static final int DISPLAY_STAT = Setting.DISPLAY_STAT;

    /* VARIABLES */
    private final App app;

    // UI
    private Dimension initSize;
    private Border onBorder;
    private final Border offBorder = new LineBorder(this.getBackground(), BORDERSIZE);
    private final List<JComboBox<String>> invComboBoxes = new ArrayList<>(4);
    private final List<JPanel> invStatPanels = new ArrayList<>(4);
    private final TipMouseListener tml;

    // Chip
    private final List<Chip> invChips = new ArrayList<>();

    // File
    private final JFileChooser iofc = new JFileChooser(new File(".")); // Inventory File Chooser
    private final JFileChooser isfc = new JFileChooser(new File(".")); // Inventory File Chooser
    private final JFileChooser cfc = new JFileChooser(new File(".")); // Combination File Chooser
    private String invFile_path = "";

    // List
    private final DefaultListModel<Chip> poolLM = new DefaultListModel<>();
    private final DefaultListModel<Chip> invLM = new DefaultListModel<>();
    private final DefaultListModel<Board> combLM = new DefaultListModel<>();
    private final DefaultListModel<Chip> combChipLM = new DefaultListModel<>();
    private final InvListCellRenderer invLCR;
    private int invListMouseDragIndex;

    // Chip Stat 
    private boolean invStat_loading;
    private int invStat_color = -1;
    private int focusedStat = FOCUSED_NONE;
    private final List<Integer> statInputBuffer = new ArrayList<>(INPUT_BUFFER_SIZE + 1);

    // Sort
    private boolean inv_order = DESCENDING;

    // Calculator
    private final Assembler calculator;
    private Progress progress;
    private long time, pauseTime;
    private long prevDoneTime;
    private final List<Long> doneTimes = new LinkedList<>();
    private final Timer calcTimer = new Timer(100, (e) -> calcTimer());

    // Setting
    private boolean settingFile_loading = false;

    // <editor-fold defaultstate="collapsed" desc="Constructor Methods">
    public MainFrame(App app) {
        this.app = app;
        initComponents();
        calculator = new Assembler(app);
        invLCR = new InvListCellRenderer(app, invList, combList, combChipList);
        tml = new TipMouseListener(tipLabel);
        init();
    }

    private void init() {
        versionLabel.setText("<html><center>Version<br>" + App.VERSION.toString() + "</center></html>");

        initImages();

        invComboBoxes.add(invDmgComboBox);
        invComboBoxes.add(invBrkComboBox);
        invComboBoxes.add(invHitComboBox);
        invComboBoxes.add(invRldComboBox);
        invStatPanels.add(invDmgPanel);
        invStatPanels.add(invBrkPanel);
        invStatPanels.add(invHitPanel);
        invStatPanels.add(invRldPanel);

        settingFile_load();

        for (String string : Board.NAMES) {
            boardNameComboBox.addItem(string);
        }

        ((JLabel) invSortTypeComboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

        poolListPanel.setBorder(offBorder);
        invListPanel.setBorder(offBorder);
        combListPanel.setBorder(offBorder);
        invStatPanels.forEach((t) -> t.setBorder(offBorder));

        invLevelSlider.setMaximum(Chip.LEVEL_MAX);
        combStopButton.setVisible(false);
        researchButton.setVisible(false);
        timeWarningButton.setVisible(false);

        initTables();
        addListeners();
        setting_resetBoard();

        packAndSetInitSize();
    }

    public void afterLoad() {
        new Thread(() -> {
            // Check app version
            IO.checkNewVersion(app);

            // Check research
            String version = ResearchConnection.getVersion();
            if (version != null && !version.isEmpty()) {
                if (!App.VERSION.isCurrent(version)) {
                    researchButton.setEnabled(false);
                }
                researchButton.setVisible(true);
            }
        }).start();
    }

    private void initImages() {
        this.setIconImage(Resources.FAVICON);
        authorButton.setIcon(Resources.getScaledIcon(Resources.UI_INFO, 16, 16));

        helpButton.setIcon(Resources.QUESTION);
        displaySettingButton.setIcon(Resources.FONT);
        poolRotLButton.setIcon(Resources.ROTATE_LEFT);
        poolRotRButton.setIcon(Resources.ROTATE_RIGHT);
        poolSortButton.setIcon(Resources.DESCENDING);

        imageButton.setIcon(Resources.PICTURE);
        proxyButton.setIcon(Resources.PHONE);

        poolWindowButton.setIcon(Resources.PANEL_CLOSE);
        addButton.setIcon(Resources.ADD);

        invNewButton.setIcon(Resources.NEW);
        invOpenButton.setIcon(Resources.OPEN);
        invSaveButton.setIcon(Resources.SAVE);
        invSaveAsButton.setIcon(Resources.SAVEAS);

        invSortOrderButton.setIcon(Resources.DESCENDING);
        filterButton.setIcon(Resources.FILTER);
        displayTypeButton.setIcon(Resources.DISPLAY_STAT);

        invRotLButton.setIcon(Resources.ROTATE_LEFT);
        invRotRButton.setIcon(Resources.ROTATE_RIGHT);
        invDelButton.setIcon(Resources.DELETE);
        invDmgTextLabel.setIcon(Resources.DMG);
        invBrkTextLabel.setIcon(Resources.BRK);
        invHitTextLabel.setIcon(Resources.HIT);
        invRldTextLabel.setIcon(Resources.RLD);

        combWarningButton.setIcon(Resources.getScaledIcon(Resources.UI_WARNING, 16, 16));
        timeWarningButton.setIcon(Resources.getScaledIcon(Resources.UI_WARNING, 16, 16));

        settingButton.setIcon(Resources.SETTING);
        combStopButton.setIcon(Resources.COMB_STOP);
        combStartPauseButton.setIcon(Resources.COMB_START);

        combDmgTextLabel.setIcon(Resources.DMG);
        combBrkTextLabel.setIcon(Resources.BRK);
        combHitTextLabel.setIcon(Resources.HIT);
        combRldTextLabel.setIcon(Resources.RLD);

        combSaveButton.setIcon(Resources.SAVE);
        combOpenButton.setIcon(Resources.OPEN);
        ticketTextLabel.setIcon(Resources.TICKET);

        legendEquippedLabel.setIcon(new ImageIcon(Resources.CHIP_EQUIPPED));
        legendRotatedLabel.setIcon(new ImageIcon(Resources.CHIP_ROTATED));
    }

    private void initTables() {
        /* POOL */
        // Model
        poolList.setModel(poolLM);
        // Renderer
        poolList.setCellRenderer(new ChipListCellRenderer(app));
        // Rows
        for (String type : Chip.TYPES) {
            for (String c : Chip.getNames(type)) {
                poolLM.addElement(new Chip(c));
            }
        }
        // Action
        pool_setOrder(app.setting.poolOrder);

        /* INVENTORY */
        invList.setFixedCellHeight(Chip.getImageHeight(true) + 3);
        invList.setFixedCellWidth(Chip.getImageWidth(true) + 3);
        Dimension invD = invListPanel.getSize();
        invD.width = invList.getFixedCellWidth() * 4 + BORDERSIZE * 2 + 10 + invListScrollPane.getVerticalScrollBar().getPreferredSize().width;
        invListPanel.setPreferredSize(invD);

        // Model
        invList.setModel(invLM);
        // Renderer
        invList.setCellRenderer(invLCR);

        // Transfer Handler
        invList.setTransferHandler(new InvListTransferHandler(this));
        invList.getActionMap().getParent().remove("cut");
        invList.getActionMap().getParent().remove("copy");
        invList.getActionMap().getParent().remove("paste");

        /* COMBINATION */
        // Model
        combList.setModel(combLM);
        // Renderer
        combList.setCellRenderer(new CombListCellRenderer(app));

        /* RESULT */
        combChipList.setFixedCellHeight(Chip.getImageHeight(true) + 3);
        combChipList.setFixedCellWidth(Chip.getImageWidth(true) + 3);
        Dimension ccD = combChipListPanel.getSize();
        ccD.width = combChipList.getFixedCellWidth() + 10 + combChipListScrollPane.getVerticalScrollBar().getPreferredSize().width;
        combChipListPanel.setPreferredSize(ccD);
        // Model
        combChipList.setModel(combChipLM);
        // Renderer
        combChipList.setCellRenderer(new ChipListCellRenderer(app));
    }

    private void addListeners() {
        // Tip 
        Fn.getAllComponents(this).forEach((t) -> t.addMouseListener(tml));

        // Shortcuts
        for (KeyListener kl : invList.getKeyListeners()) {
            invList.removeKeyListener(kl);
        }

        Fn.getAllComponents(this).stream()
                .filter((c) -> c.isFocusable())
                .forEach((c) -> c.addKeyListener(initSKA_focusable()));

        ShortcutKeyAdapter piKA = initSKA_pi();
        poolList.addKeyListener(piKA);
        invList.addKeyListener(piKA);

        poolList.addKeyListener(initSKA_pool());
        invList.addKeyListener(initSKA_inv());
        combList.addKeyListener(initSKA_comb());

        // Pool Top
        authorButton.addActionListener((e) -> Fn.popup(authorButton, "Author", Fn.toHTML(
                "바니스파 (Bunnyspa)" + System.lineSeparator()
                + "bunnyspa@naver.com"
        )));

        displaySettingButton.addActionListener((e) -> openDialog(AppSettingDialog.getInstance(app)));
        helpButton.addActionListener((e) -> openDialog(HelpDialog.getInstance(app)));
        poolWindowButton.addActionListener((e) -> setPoolPanelVisible(!poolPanel.isVisible()));

        imageButton.addActionListener((e) -> invFile_openImageDialog());
        proxyButton.addActionListener((e) -> invFile_openProxyDialog());

        // Pool Mid
        poolList.getSelectionModel().addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting()) {
                addButton.setEnabled(!poolList.isSelectionEmpty());
            }
        });
        poolList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (!poolList.isSelectionEmpty() && 2 <= evt.getClickCount()) {
                    pool_addToInv();
                }
            }
        });
        poolList.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                poolListPanel.setBorder(onBorder);
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                poolListPanel.setBorder(offBorder);
            }
        });

        // Pool Bot
        poolRotLButton.addActionListener((e) -> pool_rotate(Chip.COUNTERCLOCKWISE));
        poolRotRButton.addActionListener((e) -> pool_rotate(Chip.CLOCKWISE));
        poolSortButton.addActionListener((e) -> pool_toggleOrder());
        poolStarComboBox.addActionListener((e) -> pool_starChanged());
        poolColorButton.addActionListener((e) -> pool_cycleColor());

        // Pool Right
        addButton.addActionListener((e) -> pool_addToInv());

        // Inv Top
        invNewButton.addActionListener((e) -> invFile_new());
        invOpenButton.addActionListener((e) -> invFile_open());
        invSaveButton.addActionListener((e) -> invFile_save());
        invSaveAsButton.addActionListener((e) -> invFile_saveAs());

        invSortOrderButton.addActionListener((e) -> display_toggleOrder());
        invSortTypeComboBox.addActionListener((e) -> display_applyFilterSort());
        filterButton.addActionListener((e) -> openDialog(FilterDialog.getInstance(app)));
        displayTypeButton.addActionListener((e) -> display_toggleType());

        // Inv Mid
        invList.getSelectionModel().addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting()) {
                if (invList.getSelectedIndices().length == 1) {
                    invList.ensureIndexIsVisible(invList.getSelectedIndex());
                }
                invStat_loadStats();
            }
        });
        invList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                invListMouseDragIndex = invList.getSelectedIndex();
            }
        });
        invList.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                invListPanel.setBorder(onBorder);
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                invListPanel.setBorder(offBorder);
            }
        });

        // Inv Bot
        invApplyButton.addActionListener((e) -> openDialog(ApplyDialog.getInstance(app)));

        invLevelSlider.addChangeListener((e) -> {
            invStat_setStats();
            invStat_refreshStatComboBoxes();
        });
        invStarComboBox.addActionListener((e) -> {
            invStat_setStats();
            invStat_refreshStatComboBoxes();
        });
        invColorButton.addActionListener((e) -> invStat_cycleColor());

        invComboBoxes.forEach((t) -> t.addItemListener((e) -> invStat_setStats()));

        invRotLButton.addActionListener((e) -> invStat_rotate(Chip.COUNTERCLOCKWISE));
        invRotRButton.addActionListener((e) -> invStat_rotate(Chip.CLOCKWISE));
        invDelButton.addActionListener((e) -> invStat_delete());
        invMarkCheckBox.addItemListener((e) -> invStat_setStats());

        invTagButton.addActionListener((e) -> invStat_openTagDialog());

        // Comb Left
        combList.getSelectionModel().addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting()) {
                comb_loadCombination();
            }
        });
        combList.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                combListPanel.setBorder(onBorder);
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                combListPanel.setBorder(offBorder);
            }
        });

        // Comb Top
        boardNameComboBox.addActionListener((e) -> setting_resetBoard());
        boardStarComboBox.addActionListener((e) -> setting_resetBoard());
        settingButton.addActionListener((e) -> openDialog(CalcSettingDialog.getInstance(app)));
        combWarningButton.addActionListener((e) -> Fn.popup(combWarningButton, app.getText(Language.WARNING_HOCMAX), app.getText(Language.WARNING_HOCMAX_DESC)));
        timeWarningButton.addActionListener((e) -> Fn.popup(timeWarningButton, app.getText(Language.WARNING_TIME), app.getText(Language.WARNING_TIME_DESC)));

        showProgImageCheckBox.addItemListener((e) -> comb_setShowProgImage());
        combStartPauseButton.addActionListener((e) -> process_toggleStartPause());
        combStopButton.addActionListener((e) -> process_stop());

        // Comb Bot
        researchButton.addActionListener((e) -> openFrame(ResearchFrame.getInstance(app)));
        statButton.addActionListener((e) -> comb_openStatDialog());
        combOpenButton.addActionListener((e) -> progFile_open());
        combSaveButton.addActionListener((e) -> progFile_saveAs());

        // Comb Right
        combChipList.getSelectionModel().addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting()) {
                comb_ensureInvListIndexIsVisible();
                invList.repaint();
            }
        });
        combMarkButton.addActionListener((e) -> comb_mark());
        combTagButton.addActionListener((e) -> comb_openTagDialog());
    }

    private ShortcutKeyAdapter initSKA_focusable() {
        ShortcutKeyAdapter ska = new ShortcutKeyAdapter();

        ska.addShortcut_c(KeyEvent.VK_F, () -> openDialog(FilterDialog.getInstance(app)));
        ska.addShortcut_c(KeyEvent.VK_D, () -> display_toggleType());
        ska.addShortcut_c(KeyEvent.VK_E, () -> openDialog(CalcSettingDialog.getInstance(app)));

        ska.addShortcut(KeyEvent.VK_F1, () -> openDialog(HelpDialog.getInstance(app)));
        ska.addShortcut(KeyEvent.VK_F5, () -> process_toggleStartPause());
        ska.addShortcut(KeyEvent.VK_F6, () -> process_stop());

        return ska;
    }

    private ShortcutKeyAdapter initSKA_pi() {
        ShortcutKeyAdapter ska = new ShortcutKeyAdapter();

        ska.addShortcut_c(KeyEvent.VK_N, () -> invFile_new());
        ska.addShortcut_c(KeyEvent.VK_O, () -> invFile_open());
        ska.addShortcut_c(KeyEvent.VK_S, () -> invFile_save());

        ska.addShortcut_cs(KeyEvent.VK_S, () -> invFile_saveAs());

        ska.addShortcut(KeyEvent.VK_ENTER, () -> invStat_focusNextStat());
        for (int i = KeyEvent.VK_0; i <= KeyEvent.VK_9; i++) {
            int number = i - KeyEvent.VK_0;
            ska.addShortcut(i, () -> invStat_readInput(number));
        }
        for (int i = KeyEvent.VK_NUMPAD0; i <= KeyEvent.VK_NUMPAD9; i++) {
            int number = i - KeyEvent.VK_NUMPAD0;
            ska.addShortcut(i, () -> invStat_readInput(number));
        }

        return ska;
    }

    private ShortcutKeyAdapter initSKA_pool() {
        ShortcutKeyAdapter ska = new ShortcutKeyAdapter();

        ska.addShortcut(KeyEvent.VK_COMMA, () -> pool_rotate(Chip.COUNTERCLOCKWISE));
        ska.addShortcut(KeyEvent.VK_OPEN_BRACKET, () -> pool_rotate(Chip.COUNTERCLOCKWISE));
        ska.addShortcut(KeyEvent.VK_PERIOD, () -> pool_rotate(Chip.CLOCKWISE));
        ska.addShortcut(KeyEvent.VK_CLOSE_BRACKET, () -> pool_rotate(Chip.CLOCKWISE));
        ska.addShortcut(KeyEvent.VK_C, () -> pool_cycleColor());
        ska.addShortcut(KeyEvent.VK_R, () -> pool_toggleOrder());
        ska.addShortcut(KeyEvent.VK_SPACE, () -> pool_addToInv());

        return ska;
    }

    private ShortcutKeyAdapter initSKA_inv() {
        ShortcutKeyAdapter ska = new ShortcutKeyAdapter();

        ska.addShortcut(KeyEvent.VK_COMMA, () -> invStat_rotate(Chip.COUNTERCLOCKWISE));
        ska.addShortcut(KeyEvent.VK_OPEN_BRACKET, () -> invStat_rotate(Chip.COUNTERCLOCKWISE));
        ska.addShortcut(KeyEvent.VK_PERIOD, () -> invStat_rotate(Chip.CLOCKWISE));
        ska.addShortcut(KeyEvent.VK_CLOSE_BRACKET, () -> invStat_rotate(Chip.CLOCKWISE));
        ska.addShortcut(KeyEvent.VK_A, () -> openDialog(ApplyDialog.getInstance(app)));
        ska.addShortcut(KeyEvent.VK_C, () -> invStat_cycleColor());
        ska.addShortcut(KeyEvent.VK_M, () -> invStat_toggleMarked());
        ska.addShortcut(KeyEvent.VK_T, () -> invStat_openTagDialog());
        ska.addShortcut(KeyEvent.VK_R, () -> display_toggleOrder());
        ska.addShortcut(KeyEvent.VK_DELETE, () -> invStat_delete());
        ska.addShortcut(KeyEvent.VK_MINUS, () -> invStat_decLevel());
        ska.addShortcut(KeyEvent.VK_SUBTRACT, () -> invStat_decLevel());
        ska.addShortcut(KeyEvent.VK_EQUALS, () -> invStat_incLevel());
        ska.addShortcut(KeyEvent.VK_ADD, () -> invStat_incLevel());

        ska.addShortcut_c(KeyEvent.VK_A, () -> {
        });

        return ska;
    }

    private ShortcutKeyAdapter initSKA_comb() {
        ShortcutKeyAdapter ska = new ShortcutKeyAdapter();

        ska.addShortcut_c(KeyEvent.VK_O, () -> progFile_open());
        ska.addShortcut_c(KeyEvent.VK_S, () -> progFile_saveAs());

        ska.addShortcut(KeyEvent.VK_C, () -> comb_nextBoardName());
        ska.addShortcut(KeyEvent.VK_M, () -> comb_mark());
        ska.addShortcut(KeyEvent.VK_T, () -> comb_openTagDialog());

        return ska;
    }

    private void openDialog(JDialog dialog) {
        Fn.open(this, dialog);
    }

    private void openFrame(JFrame frame) {
        Fn.open(this, frame);
        this.setVisible(false);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Util Methods">
    public Dimension getPreferredDialogSize() {
        Dimension dim = new Dimension();
        dim.width = piButtonPanel.getWidth() + invPanel.getWidth() + combLeftPanel.getWidth() + combRightPanel.getWidth();
        dim.height = getHeight();
        return dim;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Refresh Methods">
    public void refreshDisplay() {
        refreshLang();
        refreshFont();
        refreshColor();
    }

    private void refreshLang() {
        String[] piStarCBList = new String[]{
            Board.getStarHTML_star(5),
            Board.getStarHTML_star(4),
            Board.getStarHTML_star(3),
            Board.getStarHTML_star(2)
        };
        poolStarComboBox.setModel(new DefaultComboBoxModel<>(piStarCBList));
        invStarComboBox.setModel(new DefaultComboBoxModel<>(piStarCBList));

        String[] bStarCBList = new String[]{
            Board.getStarHTML_star(5),
            Board.getStarHTML_star(4),
            Board.getStarHTML_star(3),
            Board.getStarHTML_star(2),
            Board.getStarHTML_star(1)
        };
        boardStarComboBox.setModel(new DefaultComboBoxModel<>(bStarCBList));

        invDmgTextLabel.setText(app.getText(Language.CHIP_STAT_DMG));
        invBrkTextLabel.setText(app.getText(Language.CHIP_STAT_BRK));
        invHitTextLabel.setText(app.getText(Language.CHIP_STAT_HIT));
        invRldTextLabel.setText(app.getText(Language.CHIP_STAT_RLD));
        combDmgTextLabel.setText(app.getText(Language.CHIP_STAT_DMG));
        combBrkTextLabel.setText(app.getText(Language.CHIP_STAT_BRK));
        combHitTextLabel.setText(app.getText(Language.CHIP_STAT_HIT));
        combRldTextLabel.setText(app.getText(Language.CHIP_STAT_RLD));

        invApplyButton.setText(app.getText(Language.APPLY_TITLE));
        enhancementTextLabel.setText(app.getText(Language.CHIP_LEVEL));
        invMarkCheckBox.setText(app.getText(Language.CHIP_MARK));

        researchButton.setText(app.getText(Language.RESEARCH_TITLE));
        statButton.setText(app.getText(Language.STAT_TITLE));

        ticketTextLabel.setText(app.getText(Language.CHIP_TICKET));
        xpTextLabel.setText(app.getText(Language.CHIP_XP));
        combMarkButton.setText(app.getText(Language.CHIP_MARK));
        combTagButton.setText(app.getText(Language.CHIP_TAG));

        legendEquippedLabel.setText(app.getText(Language.LEGEND_EQUIPPED));
        legendRotatedLabel.setText(app.getText(Language.LEGEND_ROTATED));

        iofc.resetChoosableFileFilters();
        isfc.resetChoosableFileFilters();
        cfc.resetChoosableFileFilters();
        iofc.setFileFilter(new FileNameExtensionFilter(app.getText(Language.FILE_EXT_INV_OPEN, IO.EXT_INVENTORY), IO.EXT_INVENTORY, "json"));
        isfc.setFileFilter(new FileNameExtensionFilter(app.getText(Language.FILE_EXT_INV_SAVE, IO.EXT_INVENTORY), IO.EXT_INVENTORY));
        cfc.setFileFilter(new FileNameExtensionFilter(app.getText(Language.FILE_EXT_COMB, IO.EXT_COMBINATION), IO.EXT_COMBINATION));

        invSortTypeComboBox.removeAllItems();
        invSortTypeComboBox.addItem(app.getText(Language.SORT_CUSTOM));
        invSortTypeComboBox.addItem(app.getText(Language.SORT_CELL));
        invSortTypeComboBox.addItem(app.getText(Language.SORT_ENHANCEMENT));
        invSortTypeComboBox.addItem(app.getText(Language.SORT_STAR));
        invSortTypeComboBox.addItem(app.getText(Language.CHIP_STAT_DMG_LONG));
        invSortTypeComboBox.addItem(app.getText(Language.CHIP_STAT_BRK_LONG));
        invSortTypeComboBox.addItem(app.getText(Language.CHIP_STAT_HIT_LONG));
        invSortTypeComboBox.addItem(app.getText(Language.CHIP_STAT_RLD_LONG));

        pool_setColorText();
        invStat_setColorText();
        display_refreshInvListCountText();
        process_setCombLabelText();
        refreshTips();

        boolean isKorean = app.setting.locale.equals(Locale.KOREA) || app.setting.locale.equals(Locale.KOREAN);
        setTitle(isKorean ? App.NAME_KR : App.NAME_EN);
    }

    private void refreshTips() {
        tml.clearTips();

        addTip(authorButton, "Author");

        addTip(displaySettingButton, app.getText(Language.TIP_DISPLAY));
        addTip(helpButton, app.getText(Language.TIP_HELP));
        addTip(imageButton, app.getText(Language.TIP_IMAGE));
        addTip(proxyButton, app.getText(Language.TIP_PROXY));

        addTip(poolList, app.getText(Language.TIP_POOL));

        addTip(poolRotLButton, app.getText(Language.TIP_POOL_ROTATE_LEFT));
        addTip(poolRotRButton, app.getText(Language.TIP_POOL_ROTATE_RIGHT));
        addTip(poolSortButton, app.getText(Language.TIP_POOL_SORT_ORDER));
        addTip(poolStarComboBox, app.getText(Language.TIP_POOL_STAR));
        addTip(poolColorButton, app.getText(Language.TIP_POOL_COLOR));

        addTip(poolWindowButton, app.getText(Language.TIP_POOLWINDOW));
        addTip(addButton, app.getText(Language.TIP_ADD));

        addTip(invList, app.getText(Language.TIP_INV));

        addTip(invNewButton, app.getText(Language.TIP_INV_NEW));
        addTip(invOpenButton, app.getText(Language.TIP_INV_OPEN));
        addTip(invSaveButton, app.getText(Language.TIP_INV_SAVE));
        addTip(invSaveAsButton, app.getText(Language.TIP_INV_SAVEAS));

        addTip(invSortOrderButton, app.getText(Language.TIP_INV_SORT_ORDER));
        addTip(invSortTypeComboBox, app.getText(Language.TIP_INV_SORT_TYPE));
        addTip(filterButton, app.getText(Language.TIP_INV_FILTER));
        addTip(displayTypeButton, app.getText(Language.TIP_INV_STAT));

        addTip(invApplyButton, app.getText(Language.TIP_INV_APPLY));
        addTip(invStarComboBox, app.getText(Language.TIP_INV_STAR));
        addTip(invColorButton, app.getText(Language.TIP_INV_COLOR));
        addTip(invLevelSlider, app.getText(Language.TIP_INV_ENHANCEMENT));
        addTip(invRotLButton, app.getText(Language.TIP_INV_ROTATE_LEFT));
        addTip(invRotRButton, app.getText(Language.TIP_INV_ROTATE_RIGHT));
        addTip(invDelButton, app.getText(Language.TIP_INV_DELETE));
        addTip(invMarkCheckBox, app.getText(Language.TIP_INV_MARK));
        addTip(invTagButton, app.getText(Language.TIP_INV_TAG));

        addTip(invDmgTextLabel, app.getText(Language.CHIP_STAT_DMG_LONG));
        addTip(invBrkTextLabel, app.getText(Language.CHIP_STAT_BRK_LONG));
        addTip(invHitTextLabel, app.getText(Language.CHIP_STAT_HIT_LONG));
        addTip(invRldTextLabel, app.getText(Language.CHIP_STAT_RLD_LONG));

        addTip(boardNameComboBox, app.getText(Language.TIP_BOARD_NAME));
        addTip(boardStarComboBox, app.getText(Language.TIP_BOARD_STAR));

        addTip(combWarningButton, app.getText(Language.WARNING_HOCMAX));
        addTip(timeWarningButton, app.getText(Language.WARNING_TIME));

        if (!researchButton.isEnabled()) {
            addTip(researchButton, app.getText(Language.TIP_RESEARCH_OLD));
        }

        addTip(combList, app.getText(Language.TIP_COMB_LIST));
        addTip(combChipList, app.getText(Language.TIP_COMB_CHIPLIST));

        addTip(combDmgTextLabel, app.getText(Language.CHIP_STAT_DMG_LONG));
        addTip(combBrkTextLabel, app.getText(Language.CHIP_STAT_BRK_LONG));
        addTip(combHitTextLabel, app.getText(Language.CHIP_STAT_HIT_LONG));
        addTip(combRldTextLabel, app.getText(Language.CHIP_STAT_RLD_LONG));

        addTip(settingButton, app.getText(Language.TIP_COMB_SETTING));
        addTip(showProgImageCheckBox, app.getText(Language.TIP_COMB_SHOWPROGIMAGE));
        addTip(combStartPauseButton, app.getText(Language.TIP_COMB_START));
        addTip(statButton, app.getText(Language.TIP_COMB_STAT));
        addTip(combOpenButton, app.getText(Language.TIP_COMB_OPEN));
        addTip(combSaveButton, app.getText(Language.TIP_COMB_SAVE));
        addTip(combMarkButton, app.getText(Language.TIP_COMB_MARK));
        addTip(combTagButton, app.getText(Language.TIP_COMB_TAG));
    }

    private void addTip(JComponent c, String s) {
        tml.setTip(c, s);
        c.setToolTipText(s);
    }

    private void refreshFont() {
        Font defaultFont = Resources.getDefaultFont().deriveFont((float) app.setting.fontSize);
        // Font
        invTagButton.setText("");
        Fn.setUIFont(defaultFont);
        Fn.getAllComponents(this).forEach((c) -> c.setFont(defaultFont));

        // Size
        combWarningButton.setPreferredSize(new Dimension(combWarningButton.getHeight(), combWarningButton.getHeight()));
        timeWarningButton.setPreferredSize(new Dimension(timeWarningButton.getHeight(), timeWarningButton.getHeight()));
        combImageLabel.setPreferredSize(new Dimension(combImageLabel.getWidth(), combImageLabel.getWidth()));

        int height = Fn.getHeight(defaultFont);
        int levelWidth = 0;
        for (int i = 0; i <= 20; i++) {
            levelWidth = Math.max(levelWidth, Fn.getWidth(String.valueOf(i), defaultFont));
        }
        invLevelLabel.setPreferredSize(new Dimension(levelWidth + 10, height));

        int textWidth = Fn.max(
                Fn.getWidth(app.getText(Language.CHIP_STAT_DMG), defaultFont),
                Fn.getWidth(app.getText(Language.CHIP_STAT_BRK), defaultFont),
                Fn.getWidth(app.getText(Language.CHIP_STAT_HIT), defaultFont),
                Fn.getWidth(app.getText(Language.CHIP_STAT_RLD), defaultFont)
        );
        Dimension textDim = new Dimension(textWidth + 30, height);
        invDmgTextLabel.setPreferredSize(textDim);
        invBrkTextLabel.setPreferredSize(textDim);
        invHitTextLabel.setPreferredSize(textDim);
        invRldTextLabel.setPreferredSize(textDim);

        int statWidth = 0;
        for (int i = 0; i <= Chip.PT_MAX; i++) {
            statWidth = Math.max(statWidth, Fn.getWidth(String.valueOf(i), defaultFont));
        }

        Dimension ptDim = new Dimension(statWidth + 10, height);
        invDmgPtLabel.setPreferredSize(ptDim);
        invBrkPtLabel.setPreferredSize(ptDim);
        invHitPtLabel.setPreferredSize(ptDim);
        invRldPtLabel.setPreferredSize(ptDim);

        int colorWidth = 0;
        for (String color : Chip.COLORSTRS.values()) {
            colorWidth = Math.max(colorWidth, Fn.getWidth(app.getText(color), defaultFont));
        }
        invColorButton.setPreferredSize(new Dimension(colorWidth + 10, height));

        int prefCombWidth = combStatPanel.getPreferredSize().width;
        combLabelPanel.setPreferredSize(new Dimension(prefCombWidth, prefCombWidth));

        // Save
        packAndSetInitSize();
        invStat_setTagButtonText();
    }

    private void refreshColor() {
        onBorder = new LineBorder(app.blue(), BORDERSIZE);
        for (int i = 0; i < poolLM.size(); i++) {
            Chip c = (Chip) poolLM.get(i);
            c.repaint();
        }
        comb_loadCombination();
    }

    public void packAndSetInitSize() {
        pack();
        initSize = getSize();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Pool Methods">
    private void pool_rotate(boolean direction) {
        for (Enumeration<Chip> elements = poolLM.elements(); elements.hasMoreElements();) {
            Chip c = elements.nextElement();
            c.initRotate(direction);
        }
        poolList.repaint();
    }

    private void pool_setOrder(boolean b) {
        if (!poolLM.isEmpty()) {
            app.setting.poolOrder = b;
            Chip c = (Chip) poolLM.firstElement();
            if (b == ASCENDING) {
                poolSortButton.setIcon(Resources.ASCNEDING);
                if (c.getSize() != 1) {
                    pool_reverseList();
                }
            } else {
                poolSortButton.setIcon(Resources.DESCENDING);
                if (c.getSize() != 6) {
                    pool_reverseList();
                }
            }
            settingFile_save();
        }
    }

    private void pool_toggleOrder() {
        pool_setOrder(!app.setting.poolOrder);
    }

    private void pool_reverseList() {
        int sel = poolList.getSelectedIndex();
        List<Chip> cs = Collections.list(poolLM.elements());
        Collections.reverse(cs);
        poolLM.clear();
        cs.forEach((c) -> poolLM.addElement(c));
        if (sel > -1) {
            sel = cs.size() - sel - 1;
            poolList.setSelectedIndex(sel);
            poolList.ensureIndexIsVisible(sel);
        }
    }

    private void pool_setColor(int color) {
        app.setting.poolColor = color;
        pool_setColorText();
        settingFile_save();
    }

    private void pool_setColorText() {
        poolColorButton.setText(app.getText(Chip.COLORSTRS.get(app.setting.poolColor)));
        poolColorButton.setForeground(Chip.COLORS.get(app.setting.poolColor));
    }

    private void pool_cycleColor() {
        pool_setColor((app.setting.poolColor + 1) % Chip.COLORSTRS.size());
    }

    private void pool_starChanged() {
        app.setting.poolStar = 5 - poolStarComboBox.getSelectedIndex();
        settingFile_save();
    }

    private void setPoolPanelVisible(boolean b) {
        if (b) {
            poolPanel.setVisible(true);
            poolWindowButton.setIcon(Resources.PANEL_CLOSE);
        } else {
            poolPanel.setVisible(false);
            poolList.clearSelection();
            poolWindowButton.setIcon(Resources.PANEL_OPEN);
        }
        if (getSize().equals(initSize)) {
            packAndSetInitSize();
        }
        settingFile_save();
    }

    private void pool_addToInv() {
        if (!poolList.isSelectionEmpty()) {
            Chip poolChip = poolList.getSelectedValue();
            Chip c = new Chip(poolChip, 5 - poolStarComboBox.getSelectedIndex(), app.setting.poolColor);
            if (invList.getSelectedIndices().length == 1) {
                int i = invList.getSelectedIndex() + 1;
                inv_chipsAdd(i, c);
                invList.setSelectedIndex(i);
            } else {
                inv_chipsAdd(c);
            }
            invStat_enableSave();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Inventory Chip Methods">
    private void inv_chipsAdd(int i, Chip c) {
        invChips.add(i, c);
        invLM.add(i, c);
        c.setDisplayType(app.setting.displayType);
    }

    private void inv_chipsAdd(Chip c) {
        invChips.add(c);
        invLM.addElement(c);
        c.setDisplayType(app.setting.displayType);
    }

    public void inv_chipsLoad(Collection<Chip> cs) {
        inv_chipsClear();
        invChips.addAll(cs);
        invChips.forEach((c) -> c.setDisplayType(app.setting.displayType));
        display_applyFilterSort();
    }

    private void inv_chipsClear() {
        invChips.clear();
        invLM.clear();
    }

    private void inv_chipsRemove(int i) {
        invChips.remove((Chip) invLM.get(i));
        invLM.removeElementAt(i);
    }

    private void inv_chipsRefresh() {
        invChips.clear();
        for (Enumeration<Chip> elements = invLM.elements(); elements.hasMoreElements();) {
            Chip c = elements.nextElement();
            invChips.add(c);
        }
    }

    public List<Chip> inv_getFilteredChips() {
        List<Chip> chips = new ArrayList<>();
        for (int i = 0; i < invLM.size(); i++) {
            chips.add((Chip) invLM.getElementAt(i));
        }
        return chips;
    }

    public List<Tag> inv_getAllTags() {
        return Tag.getTags(invChips);
    }

    public void invListTransferHandler_ExportDone() {
        inv_chipsRefresh();
        if (invListMouseDragIndex != invList.getSelectedIndex()) {
            invStat_enableSave();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Inventory Stat Methods">
    public void invStat_loadStats() {
        if (!invStat_loading) {
            invStat_loading = true;
            boolean singleSelected = invList.getSelectedIndices().length == 1;
            boolean multipleSelected = invList.getSelectedIndices().length >= 1;

            invComboBoxes.forEach((t) -> t.setEnabled(singleSelected));
            invStarComboBox.setEnabled(singleSelected);
            invLevelSlider.setEnabled(singleSelected);
            invColorButton.setEnabled(singleSelected);
            invMarkCheckBox.setEnabled(singleSelected);
            invStat_resetFocus(singleSelected);

            invDelButton.setEnabled(multipleSelected);
            invRotLButton.setEnabled(multipleSelected);
            invRotRButton.setEnabled(multipleSelected);

            invTagButton.setEnabled(multipleSelected);

            invStarComboBox.setSelectedIndex(singleSelected ? 5 - invList.getSelectedValue().getStar() : 0);
            invLevelSlider.setValue(singleSelected ? invList.getSelectedValue().getLevel() : 0);
            invStat_setColor(singleSelected ? invList.getSelectedValue().getColor() : -1);
            invMarkCheckBox.setSelected(singleSelected ? invList.getSelectedValue().isMarked() : false);

            invStat_setTagButtonText();

            invStat_loading = false;
        }
        invStat_refreshStatComboBoxes();
        invStat_refreshLabels();
    }

    private void invStat_setTagButtonText() {
        String tagButtonText = app.getText(Language.TAG_NONE);
        if (invList.getSelectedIndices().length >= 1) {
            Set<Tag> tags = new HashSet<>();
            invChips.forEach((c) -> tags.addAll(c.getTags()));
            for (int selectedIndex : invList.getSelectedIndices()) {
                Chip c = invLM.get(selectedIndex);
                tags.retainAll(c.getTags());
            }

            String widthStr = "";
            List<String> tagStrs = new ArrayList<>();
            boolean ellipsis = false;
            for (Tag t : tags) {
                int width = invTagButton.getWidth() - 10;
                String next = t.getName();

                while (!next.isEmpty() && Fn.getWidth(widthStr + next + " ...", invTagButton.getFont()) >= width) {
                    ellipsis = true;
                    next = next.substring(0, next.length() - 1);
                }
                if (next.isEmpty()) {
                    break;
                }
                tagStrs.add(Fn.htmlColor(next, t.getColor()));
                widthStr += t.getName() + ", ";
            }

            String text = String.join(", ", tagStrs) + (ellipsis ? " ..." : "");
            if (!text.isEmpty()) {
                tagButtonText = Fn.toHTML(text);
            }
        }
        invTagButton.setText(tagButtonText);
    }

    private void invStat_setStats() {
        if (!invStat_loading) {
            if (invList.getSelectedIndices().length == 1) {
                Chip c = invList.getSelectedValue();
                c.setPt(
                        invDmgComboBox.getSelectedIndex(),
                        invBrkComboBox.getSelectedIndex(),
                        invHitComboBox.getSelectedIndex(),
                        invRldComboBox.getSelectedIndex()
                );
                c.setStar(5 - invStarComboBox.getSelectedIndex());
                c.setInitLevel(invLevelSlider.getValue());
                c.setColor(invStat_color);
                c.setMarked(invMarkCheckBox.isSelected());
                c.repaint();
                invStat_enableSave();
                comb_updateMark();
            }
            invList.repaint();
            invStat_refreshLabels();
        }
    }

    public void invStat_enableSave() {
        invSaveButton.setEnabled(true);
    }

    private void invStat_refreshLabels() {
        boolean singleSelected = invList.getSelectedIndices().length == 1;

        if (singleSelected) {
            invDmgPtLabel.setText(String.valueOf(invDmgComboBox.getSelectedIndex()));
            invBrkPtLabel.setText(String.valueOf(invBrkComboBox.getSelectedIndex()));
            invHitPtLabel.setText(String.valueOf(invHitComboBox.getSelectedIndex()));
            invRldPtLabel.setText(String.valueOf(invRldComboBox.getSelectedIndex()));
            invLevelLabel.setText(String.valueOf(invLevelSlider.getValue()));
        } else {
            invDmgPtLabel.setText("");
            invBrkPtLabel.setText("");
            invHitPtLabel.setText("");
            invRldPtLabel.setText("");
            invLevelLabel.setText("");
        }

        if (singleSelected && !invList.getSelectedValue().isPtValid()) {
            invDmgPtLabel.setForeground(Color.RED);
            invBrkPtLabel.setForeground(Color.RED);
            invHitPtLabel.setForeground(Color.RED);
            invRldPtLabel.setForeground(Color.RED);
        } else {
            invDmgPtLabel.setForeground(Color.BLACK);
            invBrkPtLabel.setForeground(Color.BLACK);
            invHitPtLabel.setForeground(Color.BLACK);
            invRldPtLabel.setForeground(Color.BLACK);
        }
    }

    private void invStat_refreshStatComboBoxes() {
        if (!invStat_loading) {
            invStat_loading = true;
            invComboBoxes.forEach((t) -> t.removeAllItems());

            if (invList.getSelectedIndices().length == 1) {
                Chip c = invList.getSelectedValue();
                for (int i = 0; i <= c.getMaxPt(); i++) {
                    invDmgComboBox.addItem(String.valueOf(Chip.getStat(Chip.RATE_DMG, c, i)));
                    invBrkComboBox.addItem(String.valueOf(Chip.getStat(Chip.RATE_BRK, c, i)));
                    invHitComboBox.addItem(String.valueOf(Chip.getStat(Chip.RATE_HIT, c, i)));
                    invRldComboBox.addItem(String.valueOf(Chip.getStat(Chip.RATE_RLD, c, i)));
                }
                invDmgComboBox.setSelectedIndex(c.getPt().dmg);
                invBrkComboBox.setSelectedIndex(c.getPt().brk);
                invHitComboBox.setSelectedIndex(c.getPt().hit);
                invRldComboBox.setSelectedIndex(c.getPt().rld);
            }
            invStat_loading = false;
        }
    }

    private void invStat_setColor(int color) {
        invStat_color = color;
        invStat_setColorText();
        if (invList.getSelectedIndices().length == 1) {
            invStat_setStats();
        }
    }

    private void invStat_setColorText() {
        if (invStat_color < 0) {
            invColorButton.setText(" ");
        } else {
            invColorButton.setText(app.getText(Chip.COLORSTRS.get(invStat_color)));
            invColorButton.setForeground(Chip.COLORS.get(invStat_color));
        }
    }

    private void invStat_cycleColor() {
        if (invList.getSelectedIndices().length == 1) {
            invStat_setColor((invStat_color + 1) % Chip.COLORSTRS.size());
        }
    }

    private void invStat_setLevel(int i) {
        if (invList.getSelectedIndices().length == 1) {
            invLevelSlider.setValue(Fn.limit(i, 0, Chip.LEVEL_MAX));
        }
    }

    private void invStat_decLevel() {
        invStat_setLevel(invLevelSlider.getValue() - 1);
    }

    private void invStat_incLevel() {
        invStat_setLevel(invLevelSlider.getValue() + 1);
    }

    private void invStat_toggleMarked() {
        if (invList.getSelectedIndices().length == 1) {
            invMarkCheckBox.setSelected(!invMarkCheckBox.isSelected());
        }
    }

    private void invStat_openTagDialog() {
        if (invList.getSelectedIndices().length >= 1) {
            List<Chip> chips = new ArrayList<>(invList.getSelectedIndices().length);
            for (int selectedIndex : invList.getSelectedIndices()) {
                Chip c = invLM.get(selectedIndex);
                chips.add(c);
            }
            openDialog(TagDialog.getInstance(app, chips));
        }
    }

    private void invStat_focusStat(int type) {
        focusedStat = type;
        for (int i = 0; i < 4; i++) {
            invStatPanels.get(i).setBorder(type == i ? onBorder : offBorder);
        }
        statInputBuffer.clear();
    }

    private void invStat_resetFocus(boolean focused) {
        invStat_focusStat(focused ? FOCUSED_DMG : FOCUSED_NONE);
    }

    private void invStat_focusNextStat() {
        if (invList.getSelectedIndices().length == 1) {
            invStat_focusStat((focusedStat + 1) % 4);
        }
    }

    private void invStat_readInput(int number) {
        if (invList.getSelectedIndices().length == 1) {
            statInputBuffer.add(number);
            if (statInputBuffer.size() > INPUT_BUFFER_SIZE) {
                statInputBuffer.remove(0);
            }
            String[] inputs = new String[statInputBuffer.size()];
            for (int i = 0; i < inputs.length; i++) {
                if (statInputBuffer.size() >= i + 1) {
                    String t = "";
                    for (int j = i; j < statInputBuffer.size(); j++) {
                        t += String.valueOf(statInputBuffer.get(j));
                    }
                    inputs[i] = t;
                }
            }

            JComboBox<String> combobox = invComboBoxes.get(focusedStat);
            int nItems = combobox.getItemCount();
            if (app.setting.displayType == DISPLAY_STAT) {
                for (int i = nItems - 1; i >= 0; i--) {
                    String candidate = combobox.getItemAt(i);
                    for (String input : inputs) {
                        if (candidate.equals(input)) {
                            combobox.setSelectedIndex(i);
                            return;
                        }
                    }
                }
            } else {
                int pt = Integer.valueOf(inputs[inputs.length - 1]);
                if (pt < nItems) {
                    combobox.setSelectedIndex(pt);
                }
            }
        }

    }

    public void invStat_applyAll(Consumer<? super Chip> action) {
        List<Chip> cs = inv_getFilteredChips();
        if (!cs.isEmpty()) {
            cs.forEach(action);
            invList.repaint();
            invStat_loadStats();
            invStat_enableSave();
        }
    }

    private void invStat_rotate(boolean direction) {
        if (invList.getSelectedIndices().length >= 1) {
            for (int selectedIndex : invList.getSelectedIndices()) {
                Chip c = invLM.get(selectedIndex);
                c.initRotate(direction);
            }

            invList.repaint();
            invStat_enableSave();
        }
    }

    private void invStat_delete() {
        if (invList.getSelectedIndices().length >= 1) {
            int[] indices = invList.getSelectedIndices();
            List<Integer> indexList = new ArrayList<>(indices.length);
            for (int i : indices) {
                indexList.add(i);
            }
            Collections.reverse(indexList);

            indexList.forEach((selectedIndex) -> {
                inv_chipsRemove(selectedIndex);
            });
            display_refreshInvListCountText();
            invStat_enableSave();
        }
    }
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Inventory Display Methods">
    private void display_setOrder(boolean order) {
        if (invSortOrderButton.isEnabled()) {
            inv_order = order;
            if (order == ASCENDING) {
                invSortOrderButton.setIcon(Resources.ASCNEDING);
            } else {
                invSortOrderButton.setIcon(Resources.DESCENDING);
            }
            display_applyFilterSort();
        }
    }

    private void display_toggleOrder() {
        display_setOrder(!inv_order);
    }

    private boolean display_anyTrueFilter() {
        return app.filter.anySCTMTrue()
                || app.filter.levelMin != 0 || app.filter.levelMax != Chip.LEVEL_MAX
                || !app.filter.ptMin.equals(new Stat())
                || !app.filter.ptMax.equals(new Stat(Chip.PT_MAX))
                || !app.filter.includedTags.isEmpty()
                || !app.filter.excludedTags.isEmpty();
    }

    public void display_applyFilterSort() {
        invLM.removeAllElements();
        List<Chip> temp = new ArrayList<>();

        //// Filter
        invChips.forEach((c) -> {
            boolean pass = true;
            // Star
            if (app.filter.anyStarTrue()) {
                int i = 5 - c.getStar();
                pass = app.filter.getStar(i);
            }
            // Color
            if (pass && app.filter.anyColorTrue()) {
                int i = c.getColor();
                pass = app.filter.getColor(i);
            }
            // Size
            if (pass && app.filter.anyTypeTrue()) {
                int i = 6 - c.getSize();
                if (c.getSize() < 5 || Chip.TYPE_5A.equals(c.getType())) {
                    i++;
                }
                pass = app.filter.getType(i);
            }
            // Marked
            if (pass && app.filter.anyMarkTrue()) {
                int i = c.isMarked() ? 0 : 1;
                pass = app.filter.getMark(i);
            }
            // Level
            if (pass) {
                pass = app.filter.levelMin <= c.getLevel() && c.getLevel() <= app.filter.levelMax;
            }
            // PT
            if (pass) {
                Stat cPt = c.getPt();
                pass = cPt.allGeq(app.filter.ptMin) && cPt.allLeq(app.filter.ptMax);
            }
            // Tag
            if (pass && !app.filter.includedTags.isEmpty()) {
                pass = app.filter.includedTags.stream().allMatch((fTag) -> c.getTags().stream().anyMatch((cTag) -> fTag.equals(cTag)));
            }
            if (pass && !app.filter.excludedTags.isEmpty()) {
                pass = app.filter.excludedTags.stream().noneMatch((fTag) -> c.getTags().stream().anyMatch((cTag) -> fTag.equals(cTag)));
            }

            // Final
            if (pass) {
                temp.add(c);
            }
        });

        //// Sort
        switch (invSortTypeComboBox.getSelectedIndex()) {
            case SORT_SIZE:
                temp.sort((c1, c2) -> Chip.compare(c1, c2));
                break;
            case SORT_LEVEL:
                temp.sort((c1, c2) -> Chip.compareLevel(c1, c2));
                break;
            case SORT_STAR:
                temp.sort((c1, c2) -> Chip.compareStar(c1, c2));
                break;
            case SORT_DMG:
                temp.sort((c1, c2)
                        -> app.setting.displayType == Setting.DISPLAY_STAT
                                ? c1.getStat().dmg - c2.getStat().dmg
                                : c1.getPt().dmg - c2.getPt().dmg);
                break;
            case SORT_BRK:
                temp.sort((c1, c2)
                        -> app.setting.displayType == Setting.DISPLAY_STAT
                                ? c1.getStat().brk - c2.getStat().brk
                                : c1.getPt().brk - c2.getPt().brk);
                break;
            case SORT_HIT:
                temp.sort((c1, c2)
                        -> app.setting.displayType == Setting.DISPLAY_STAT
                                ? c1.getStat().hit - c2.getStat().hit
                                : c1.getPt().hit - c2.getPt().hit);
                break;
            case SORT_RLD:
                temp.sort((c1, c2)
                        -> app.setting.displayType == Setting.DISPLAY_STAT
                                ? c1.getStat().rld - c2.getStat().rld
                                : c1.getPt().rld - c2.getPt().rld);
                break;
            default:
        }

        if (invSortTypeComboBox.getSelectedIndex() != SORT_NONE && inv_order == DESCENDING) {
            Collections.reverse(temp);
        }

        // Fill
        temp.forEach((c) -> {
            invLM.addElement(c);
        });

        boolean anyTrueAll = display_anyTrueFilter();

        // UI
        invSortOrderButton.setEnabled(invSortTypeComboBox.getSelectedIndex() != SORT_NONE);
        boolean chipEnabled = invSortTypeComboBox.getSelectedIndex() == SORT_NONE && !anyTrueAll;
        poolList.setEnabled(chipEnabled);
        if (!chipEnabled) {
            poolList.clearSelection();
        }
        addButton.setEnabled(chipEnabled && poolList.getSelectedIndex() != -1);
        invList.setDragEnabled(chipEnabled);
        filterButton.setIcon(anyTrueAll ? Resources.FILTER_APPLY : Resources.FILTER);
        display_refreshInvListCountText();
    }

    private void display_refreshInvListCountText() {
        filterChipCountLabel.setText(
                display_anyTrueFilter()
                        ? app.getText(Language.FILTER_ENABLED, String.valueOf(invLM.size()), String.valueOf(invChips.size()))
                        : app.getText(Language.FILTER_DISABLED, String.valueOf(invChips.size())
                        )
        );
    }

    private void display_setType(int i) {
        int iMod = i % Setting.NUM_DISPLAY;
        app.setting.displayType = iMod;
        if (iMod == DISPLAY_STAT) {
            displayTypeButton.setIcon(Resources.DISPLAY_STAT);
        } else {
            displayTypeButton.setIcon(Resources.DISPLAY_PT);
        }
        invChips.forEach((t) -> t.setDisplayType(iMod));
        display_applyFilterSort();
        invList.repaint();
        for (int b = 0; b < combLM.size(); b++) {
            Board board = (Board) combLM.get(b);
            board.forEachChip((t) -> t.setDisplayType(iMod));
        }
        combChipList.repaint();
        settingFile_save();
    }

    private void display_toggleType() {
        display_setType(app.setting.displayType + 1);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Setting Methods">
    public final String getBoardName() {
        return boardNameComboBox.getItemAt(boardNameComboBox.getSelectedIndex());
    }

    public final int getBoardStar() {
        return 5 - boardStarComboBox.getSelectedIndex();
    }

    public void setting_resetDisplay() {
        Icon settingIcon;
        switch (app.setting.board.getMode(getBoardName(), getBoardStar())) {
            case BoardSetting.MAX_STAT:
                settingIcon = Resources.SETTING_STAT;
                break;
            case BoardSetting.MAX_PT:
                settingIcon = Resources.SETTING_PT;
                break;
            case BoardSetting.MAX_PRESET:
                settingIcon = Resources.SETTING_PRESET;
                break;
            default:
                settingIcon = Resources.SETTING;
        }
        settingButton.setIcon(settingIcon);
        BoardSetting board = app.setting.board;
        boolean maxWarning = getBoardStar() == 5
                && board.getMode(getBoardName(), getBoardStar()) != BoardSetting.MAX_PRESET
                && !board.hasDefaultPreset(getBoardName(), getBoardStar());
        combWarningButton.setVisible(maxWarning);
    }

    private void setting_resetBoard() {
        setting_resetDisplay();
        boardImageLabel.setIcon(Board.getImage(app, boardImageLabel.getWidth(), getBoardName(), getBoardStar()));
        boardImageLabel.repaint();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Combination Methods">
    private void comb_setBoardName(int i) {
        if (boardNameComboBox.isEnabled()) {
            boardNameComboBox.setSelectedIndex(Fn.limit(i, 0, boardNameComboBox.getItemCount()));
        }
    }

    private void comb_nextBoardName() {
        comb_setBoardName((boardNameComboBox.getSelectedIndex() + 1) % boardNameComboBox.getItemCount());
    }

    private void comb_setShowProgImage() {
        app.setting.showProgImage = showProgImageCheckBox.isSelected();
        if (!app.setting.showProgImage) {
            boardImageLabel.setIcon(Board.getImage(app, boardImageLabel.getWidth(), getBoardName(), getBoardStar()));
            boardImageLabel.repaint();
        }
    }

    public void comb_loadCombination() {
        combChipLM.clear();
        boolean selected = !combList.isSelectionEmpty();
        statButton.setEnabled(selected);
        combMarkButton.setEnabled(selected);
        combTagButton.setEnabled(selected);
        if (selected) {
            Board board = combList.getSelectedValue();
            combImageLabel.setIcon(board.getImage(app, Math.min(combImageLabel.getHeight(), combImageLabel.getWidth()) - 1));
            combImageLabel.setText("");

            board.forEachChip((c) -> {
                c.setDisplayType(app.setting.displayType);
                combChipLM.addElement(c);
            });
            comb_updateMark();

            Stat stat = board.getStat();
            Stat cMax = board.getCustomMaxStat();
            Stat oMax = board.getOrigMaxStat();
            Stat resonance = board.getResonance();
            Stat pt = board.getPt();

            combDmgStatLabel.setText(stat.dmg + " / " + cMax.dmg + (cMax.dmg == oMax.dmg ? "" : " (" + oMax.dmg + ")"));
            combBrkStatLabel.setText(stat.brk + " / " + cMax.brk + (cMax.brk == oMax.brk ? "" : " (" + oMax.brk + ")"));
            combHitStatLabel.setText(stat.hit + " / " + cMax.hit + (cMax.hit == oMax.hit ? "" : " (" + oMax.hit + ")"));
            combRldStatLabel.setText(stat.rld + " / " + cMax.rld + (cMax.rld == oMax.rld ? "" : " (" + oMax.rld + ")"));
            combDmgStatLabel.setForeground(stat.dmg >= cMax.dmg ? Color.RED : Color.BLACK);
            combBrkStatLabel.setForeground(stat.brk >= cMax.brk ? Color.RED : Color.BLACK);
            combHitStatLabel.setForeground(stat.hit >= cMax.hit ? Color.RED : Color.BLACK);
            combRldStatLabel.setForeground(stat.rld >= cMax.rld ? Color.RED : Color.BLACK);

            combDmgPercLabel.setText(Fn.fPercStr(board.getStatPerc(Stat.DMG))
                    + (cMax.dmg == oMax.dmg
                            ? ""
                            : " (" + Fn.iPercStr(Board.getStatPerc(Stat.DMG, stat, oMax)) + ")"));
            combBrkPercLabel.setText(Fn.fPercStr(board.getStatPerc(Stat.BRK))
                    + (cMax.brk == oMax.brk
                            ? ""
                            : " (" + Fn.iPercStr(Board.getStatPerc(Stat.BRK, stat, oMax)) + ")"));
            combHitPercLabel.setText(Fn.fPercStr(board.getStatPerc(Stat.HIT))
                    + (cMax.hit == oMax.hit
                            ? ""
                            : " (" + Fn.iPercStr(Board.getStatPerc(Stat.HIT, stat, oMax)) + ")"));
            combRldPercLabel.setText(Fn.fPercStr(board.getStatPerc(Stat.RLD))
                    + (cMax.rld == oMax.rld
                            ? ""
                            : " (" + Fn.iPercStr(Board.getStatPerc(Stat.RLD, stat, oMax)) + ")"));

            combDmgPtLabel.setText(app.getText(Language.UNIT_PT, String.valueOf(pt.dmg)));
            combBrkPtLabel.setText(app.getText(Language.UNIT_PT, String.valueOf(pt.brk)));
            combHitPtLabel.setText(app.getText(Language.UNIT_PT, String.valueOf(pt.hit)));
            combRldPtLabel.setText(app.getText(Language.UNIT_PT, String.valueOf(pt.rld)));
            combDmgPtLabel.setForeground(Color.BLACK);
            combBrkPtLabel.setForeground(Color.BLACK);
            combHitPtLabel.setForeground(Color.BLACK);
            combRldPtLabel.setForeground(Color.BLACK);

            combDmgResonanceStatLabel.setText("+" + resonance.dmg);
            combBrkResonanceStatLabel.setText("+" + resonance.brk);
            combHitResonanceStatLabel.setText("+" + resonance.hit);
            combRldResonanceStatLabel.setText("+" + resonance.rld);
            combDmgResonanceStatLabel.setForeground(Chip.COLORS.get(board.getColor()));
            combBrkResonanceStatLabel.setForeground(Chip.COLORS.get(board.getColor()));
            combHitResonanceStatLabel.setForeground(Chip.COLORS.get(board.getColor()));
            combRldResonanceStatLabel.setForeground(Chip.COLORS.get(board.getColor()));

            ticketLabel.setText(String.valueOf(board.getTicketCount()));
            xpLabel.setText(Fn.thousandComma(board.getXP()));
        } else {
            combImageLabel.setIcon(null);
            combImageLabel.setText(app.getText(Language.COMB_DESC));

            combDmgStatLabel.setForeground(Color.BLACK);
            combDmgStatLabel.setText("");
            combBrkStatLabel.setText("");
            combHitStatLabel.setText("");
            combRldStatLabel.setText("");

            combDmgPercLabel.setForeground(Color.BLACK);
            combDmgPercLabel.setText("");
            combBrkPercLabel.setText("");
            combHitPercLabel.setText("");
            combRldPercLabel.setText("");

            combDmgPtLabel.setForeground(Color.BLACK);
            combDmgPtLabel.setText("");
            combBrkPtLabel.setText("");
            combHitPtLabel.setText("");
            combRldPtLabel.setText("");

            combDmgResonanceStatLabel.setForeground(Color.BLACK);
            combDmgResonanceStatLabel.setText("");
            combBrkResonanceStatLabel.setText("");
            combHitResonanceStatLabel.setText("");
            combRldResonanceStatLabel.setText("");

            ticketLabel.setText("-");
            xpLabel.setText("-");
        }
        invList.repaint();
    }

    private void comb_updateMark() {
        Enumeration<Chip> combChips = combChipLM.elements();
        while (combChips.hasMoreElements()) {
            Chip c = combChips.nextElement();
            for (Chip invChip : invChips) {
                if (invChip.equals(c)) {
                    c.setMarked(invChip.isMarked());
                    break;
                }
            }
        }
        combChipList.repaint();
    }

    private List<Chip> comb_getChipsFromInv() {
        Enumeration<Chip> chipEnum = combChipLM.elements();
        List<Chip> chipList = new ArrayList<>();
        while (chipEnum.hasMoreElements()) {
            Chip c = chipEnum.nextElement();
            for (Chip invChip : invChips) {
                if (invChip.equals(c)) {
                    chipList.add(invChip);
                    break;
                }
            }
        }
        return chipList;
    }

    private void comb_openStatDialog() {
        if (!combList.isSelectionEmpty()) {
            Board board = combList.getSelectedValue();
            StatDialog.open(app, board);
        }
    }

    private void comb_mark() {
        if (!combList.isSelectionEmpty()) {
            List<Chip> chipList = comb_getChipsFromInv();
            int retval = JOptionPane.showConfirmDialog(this,
                    app.getText(Language.COMB_MARK_CONTINUE_BODY), app.getText(Language.COMB_MARK_CONTINUE_TITLE),
                    JOptionPane.YES_NO_OPTION);
            if (retval == JOptionPane.YES_OPTION && combChipLM.size() != chipList.size()) {
                retval = JOptionPane.showConfirmDialog(this,
                        app.getText(Language.COMB_DNE_BODY), app.getText(Language.COMB_DNE_TITLE),
                        JOptionPane.YES_NO_OPTION);
            }
            if (retval == JOptionPane.YES_OPTION) {
                chipList.forEach((c) -> {
                    c.setMarked(true);
                });
                invList.repaint();
                comb_updateMark();
                invStat_enableSave();
            }
        }
    }

    private void comb_openTagDialog() {
        if (!combList.isSelectionEmpty()) {
            List<Chip> chipList = comb_getChipsFromInv();
            int retval = JOptionPane.YES_OPTION;
            if (combChipLM.size() != chipList.size()) {
                retval = JOptionPane.showConfirmDialog(this,
                        app.getText(Language.COMB_DNE_BODY), app.getText(Language.COMB_DNE_TITLE),
                        JOptionPane.YES_NO_OPTION);
            }
            if (retval == JOptionPane.YES_OPTION) {
                openDialog(TagDialog.getInstance(app, chipList));
            }
        }
    }

    private void comb_ensureInvListIndexIsVisible() {
        if (!combChipList.isSelectionEmpty()) {
            Chip combChip = combChipList.getSelectedValue();
            for (int i = 0; i < invLM.size(); i++) {
                Chip invChip = (Chip) invLM.get(i);
                if (combChip.equals(invChip)) {
                    invList.ensureIndexIsVisible(i);
                    break;
                }
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Process Methods">
    private void process_toggleStartPause() {
        switch (calculator.getStatus()) {
            case STOPPED:
                process_start();
                break;
            case RUNNING:
                process_pause();
                break;
            case PAUSED:
                process_resume();
                break;
            default:
                throw new AssertionError();
        }
    }

    private void process_start() {
        // Check for the validity of all inventory chips
        for (Enumeration<Chip> elements = invLM.elements(); elements.hasMoreElements();) {
            Chip chip = elements.nextElement();
            if (!chip.isPtValid()) {
                JOptionPane.showMessageDialog(this,
                        app.getText(Language.COMB_ERROR_STAT_BODY),
                        app.getText(Language.COMB_ERROR_STAT_TITLE),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        String name = getBoardName();
        int star = getBoardStar();

        // init
        boolean start = true;
        int status = Progress.DICTIONARY;
        boolean alt = false;
        String minType = calculator.getMinType(name, star, false);

        // Partial option
        if (calculator.hasPartial(name, star)) {
            // Query
            String[] options = {
                app.getText(Language.COMB_OPTION_M2_0),
                app.getText(Language.COMB_OPTION_M2_1),
                app.getText(Language.COMB_OPTION_M2_2),
                app.getText(Language.ACTION_CANCEL)
            };
            int response = JOptionPane.showOptionDialog(this,
                    app.getText(Language.COMB_OPTION_M2_DESC, options[0], options[1]), app.getText(Language.COMB_OPTION_TITLE),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]
            );
            // Response
            start = response != JOptionPane.CLOSED_OPTION && response <= 2;
            status = response <= 1 ? Progress.DICTIONARY : Progress.ALGX;
            alt = response == 0;
        } //
        // Full option
        else {
            // Check if any chip size is smaller than dictionary chip size
            Enumeration<Chip> elements = invLM.elements();
            while (elements.hasMoreElements() && status == Progress.DICTIONARY) {
                Chip c = elements.nextElement();
                if (!c.typeGeq(minType)) {
                    status = Progress.ALGX;
                    break;
                }
            }
            // Query
            if (status == Progress.ALGX) {
                String combOption0Text = minType.length() > 1
                        ? app.getText(Language.UNIT_CELLTYPE, minType.substring(0, 1), minType.substring(1, 2))
                        : app.getText(Language.UNIT_CELL, minType);
                String[] options = {
                    app.getText(Language.COMB_OPTION_DEFAULT_0, combOption0Text),
                    app.getText(Language.COMB_OPTION_DEFAULT_1),
                    app.getText(Language.ACTION_CANCEL)
                };
                int response = JOptionPane.showOptionDialog(this,
                        app.getText(Language.COMB_OPTION_DEFAULT_DESC, options[0], combOption0Text),
                        app.getText(Language.COMB_OPTION_TITLE),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]
                );
                // Response
                start = response != JOptionPane.CLOSED_OPTION && response <= 1;
                status = response == 0 ? Progress.DICTIONARY : Progress.ALGX;
            }

        }

        // If preset DNE
        if (!calculator.presetExists(name, star, alt)) {
            status = Progress.ALGX;
        }

        if (start) {
            // Filter and deep-copy chips
            List<Chip> candidates = new ArrayList<>();
            for (Enumeration<Chip> elements = invLM.elements(); elements.hasMoreElements();) {
                Chip chip = elements.nextElement();
                boolean colorMatch = !app.setting.chipMatchColor || Board.getColor(name) == chip.getColor();
                boolean sizeMatch = status == Progress.ALGX || chip.typeGeq(minType);
                boolean markMatchNeg = 0 < app.setting.boardMarkMax || !chip.isMarked();
                boolean markMatchPos = app.setting.boardMarkMin < Board.getCellCount(name, star) || chip.isMarked();
                if (colorMatch && sizeMatch && markMatchNeg && markMatchPos) {
                    candidates.add(new Chip(chip));
                }
            }
            if (app.setting.chipLevelMax) {
                candidates.forEach((c) -> c.setMaxLevel());
            }

            progress = new Progress(status, name, star, candidates, app.setting, alt ? 1 : 0);
            process_init();
            process_resume();
        }
    }

    private void process_init() {
        calculator.set(progress);

        time = System.currentTimeMillis();
        pauseTime = 0;

        process_updateProgress(true);

        combStopButton.setVisible(true);

    }

    private void process_setUI(Assembler.Status status) {
        switch (status) {
            case RUNNING:
                loadingLabel.setIcon(Resources.LOADING);
                break;
            case PAUSED:
                loadingLabel.setIcon(Resources.PAUSED);
                break;
            case STOPPED:
                loadingLabel.setIcon(null);
                setting_resetBoard();
                break;
            default:
                throw new AssertionError();
        }

        if (status != Assembler.Status.RUNNING) {
            prevDoneTime = 0;
            doneTimes.clear();
        }

        combStartPauseButton.setIcon(status == Assembler.Status.RUNNING ? Resources.COMB_PAUSE : Resources.COMB_START);
        combStopButton.setVisible(status != Assembler.Status.STOPPED);

        boardNameComboBox.setEnabled(status == Assembler.Status.STOPPED);
        boardStarComboBox.setEnabled(status == Assembler.Status.STOPPED);
        settingButton.setEnabled(status == Assembler.Status.STOPPED);
        researchButton.setEnabled(status == Assembler.Status.STOPPED);
        combOpenButton.setEnabled(status == Assembler.Status.STOPPED);
        combSaveButton.setEnabled(status != Assembler.Status.RUNNING && progress != null && progress.boards.size() > 0);

        process_updateProgress(status != Assembler.Status.RUNNING);
    }

    private void calcTimer() {
        if (calculator.getStatus() == Assembler.Status.RUNNING) {
            process_updateProgress(false);
        }
    }

    private void process_pause() {
        pauseTime = System.currentTimeMillis();
        calcTimer.stop();

        process_setUI(Assembler.Status.PAUSED);
        calculator.pause();
    }

    private void process_resume() {
        if (0 < pauseTime) {
            time += System.currentTimeMillis() - pauseTime;
        }
        pauseTime = 0;
        calcTimer.start();

        process_setUI(Assembler.Status.RUNNING);
        calculator.resume();
    }

    public void process_stop() {
        if (0 < pauseTime) {
            time += System.currentTimeMillis() - pauseTime;
        }
        pauseTime = 0;
        calcTimer.stop();

        if (progress != null) {
            progress.status = Progress.FINISHED;
        }
        process_setUI(Assembler.Status.STOPPED);
        calculator.stop();
    }

    private void process_updateProgress(boolean forceUpdate) {
        process_setCombLabelText();
        process_setElapsedTime();
        process_refreshCombListModel(forceUpdate);
    }

    private void process_setCombLabelText() {
        if (progress != null && progress.status == Progress.FINISHED && 0 == progress.nComb) {
            combLabel.setText(app.getText(Language.COMB_NONEFOUND));
        } else if (progress != null && 0 <= progress.nComb) {
            combLabel.setText(Fn.thousandComma(progress.nComb));
        } else {
            combLabel.setText("");
        }
    }

    private void process_setElapsedTime() {
        StringBuilder sb = new StringBuilder();

        long sec = (System.currentTimeMillis() - time) / 1000;
        sb.append(Fn.getTime(sec));

        boolean warn = false;
        if (!doneTimes.isEmpty()) {
            long avg = doneTimes.stream().mapToLong((v) -> v).sum() / doneTimes.size();
            long remaining = avg * (progress.nTotal - progress.nDone) / 1000;
            warn = 60 * 60 < remaining;
            sb.append(" (").append(app.getText(Language.COMB_REMAINING, Fn.getTime(remaining))).append(")");
        }

        timeWarningButton.setVisible(warn);
        timeLabel.setText(sb.toString());
    }

    private void process_refreshCombListModel(boolean forceUpdate) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (forceUpdate || calculator.boardsUpdated()) {
                    Board selectedBoard = null;
                    if (!combList.isSelectionEmpty()) {
                        selectedBoard = combList.getSelectedValue();
                    }

                    combLM.clear();
                    calculator.getBoards().forEach((b) -> combLM.addElement(b));

                    if (selectedBoard != null) {
                        combList.setSelectedValue(selectedBoard, true);
                    }
                }
            } catch (Exception ex) {
            }
        });
    }

    // From Combinator
    public void process_setProgBar(int n, int max) {
        combProgressBar.setMaximum(max);
        combProgressBar.setValue(n);
    }

    public void process_showImage(PuzzlePreset preset) {
        SwingUtilities.invokeLater(() -> {
            if (app.setting.showProgImage && calculator.getStatus() == Assembler.Status.RUNNING) {
                boardImageLabel.setIcon(preset.getImage(app, boardImageLabel.getWidth()));
                boardImageLabel.repaint();
            }
        });
    }

    public void process_prog(int prog) {
        SwingUtilities.invokeLater(() -> {
            long doneTime = System.currentTimeMillis();
            if (prevDoneTime != 0) {
                long t = doneTime - prevDoneTime;
                if (doneTimes.size() == SIZE_DONETIME) {
                    doneTimes.remove(0);
                }
                doneTimes.add(t);
            }
            prevDoneTime = doneTime;
            combProgressBar.setValue(prog);
        });
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Setting File Methods">
    private void settingFile_load() {
        if (!settingFile_loading) {
            settingFile_loading = true;

            refreshDisplay();

            poolStarComboBox.setSelectedIndex(5 - app.setting.poolStar);

            setPoolPanelVisible(app.setting.poolPanelVisible);
            display_setType(app.setting.displayType);

            showProgImageCheckBox.setSelected(app.setting.showProgImage);

            settingFile_loading = false;
        }
    }

    public void settingFile_save() {
        if (!settingFile_loading) {
            IO.saveSettings(app.setting);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Inventory File Methods">
    private boolean invFile_confirmSave() {
        if (invSaveButton.isEnabled()) {
            int retval = JOptionPane.showConfirmDialog(this,
                    app.getText(Language.FILE_SAVE_BODY), app.getText(Language.FILE_SAVE_TITLE),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (retval == JOptionPane.CANCEL_OPTION) {
                return false;
            } else if (retval == JOptionPane.YES_OPTION) {
                invFile_save();
            }
        }
        return true;
    }

    private void invFile_new() {
        if (invFile_confirmSave()) {
            invFile_clear();
        }
    }

    public void invFile_clear() {
        invFile_path = "";
        fileTextArea.setText("");
        inv_chipsClear();
        invSaveButton.setEnabled(false);
    }

    private void invFile_open() {
        if (invFile_confirmSave()) {
            int retval = iofc.showOpenDialog(this);
            if (retval == JFileChooser.APPROVE_OPTION) {
                invFile_path = iofc.getSelectedFile().getPath();
                fileTextArea.setText(iofc.getSelectedFile().getName());
                inv_chipsLoad(invFile_path.endsWith("." + IO.EXT_INVENTORY)
                        ? IO.loadInventory(invFile_path)
                        : JsonFilterDialog.filter(app, this, JsonParser.readFile(invFile_path))
                );
                invSaveButton.setEnabled(false);
            }
        }
    }

    private void invFile_save() {
        if (invSaveButton.isEnabled()) {
            if (invFile_path.isEmpty()) {
                invFile_saveAs();
            } else {
                IO.saveInventory(invFile_path, invChips);
                invSaveButton.setEnabled(false);
            }
        }
    }

    private void invFile_saveAs() {
        int retval = isfc.showSaveDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String selectedPath = isfc.getSelectedFile().getPath();
            String fileName = isfc.getSelectedFile().getName();

            // Extension
            if (!selectedPath.endsWith("." + IO.EXT_INVENTORY)) {
                selectedPath += "." + IO.EXT_INVENTORY;
                fileName += "." + IO.EXT_INVENTORY;
            }

            // Overwrite
            boolean confirmed = true;
            if (isfc.getSelectedFile().exists()) {
                int option = JOptionPane.showConfirmDialog(this,
                        app.getText(Language.FILE_OVERWRITE_BODY), app.getText(Language.FILE_OVERWRITE_TITLE),
                        JOptionPane.YES_NO_OPTION);
                if (option != JOptionPane.YES_OPTION) {
                    confirmed = false;
                }
            }

            // Save
            if (confirmed) {
                invFile_path = selectedPath;
                IO.saveInventory(invFile_path, invChips);
                fileTextArea.setText(fileName);
                invSaveButton.setEnabled(false);
            }
        }
    }

    private void invFile_openImageDialog() {
        ImageDialog.getData(app).forEach((c) -> inv_chipsAdd(c));
    }

    private void invFile_openProxyDialog() {
        if (invFile_confirmSave()) {
            List<Chip> chips = ProxyDialog.extract(app);
            if (chips != null) {
                invFile_clear();
                inv_chipsLoad(chips);
                invStat_enableSave();
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Progress File Methods">
    private void progFile_open() {
        int retval = cfc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = cfc.getSelectedFile().getPath();
            progress = IO.loadProgress(path, invChips);
            combSaveButton.setEnabled(false);

            boardNameComboBox.setSelectedItem(progress.name);
            boardStarComboBox.setSelectedIndex(5 - progress.star);

            if (progress.status != Progress.FINISHED) {
                Setting setting = app.setting;

                setting.chipLevelMax = progress.maxLevel;
                setting.chipMatchColor = progress.matchColor;
                setting.chipAllowRotation = progress.allowRotation;

                setting.boardMarkMin = progress.markMin;
                setting.boardMarkMax = progress.markMax;
                setting.boardMarkType = progress.markType;
                setting.boardSortType = progress.sortType;
            }
            process_init();
            if (progress.status != Progress.FINISHED) {
                process_pause();
            }
        }
    }

    private void progFile_saveAs() {
        if (combSaveButton.isEnabled()) {
            int retval = cfc.showSaveDialog(this);
            if (retval == JFileChooser.APPROVE_OPTION) {
                String path = cfc.getSelectedFile().getPath();

                // Extension
                if (!path.endsWith("." + IO.EXT_COMBINATION)) {
                    path += "." + IO.EXT_COMBINATION;
                }

                // Save
                IO.saveProgress(path, progress);
                combSaveButton.setEnabled(false);
            }
        }
    }
    // </editor-fold>

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        poolPanel = new javax.swing.JPanel();
        poolTPanel = new javax.swing.JPanel();
        versionLabel = new javax.swing.JLabel();
        helpButton = new javax.swing.JButton();
        displaySettingButton = new javax.swing.JButton();
        authorButton = new javax.swing.JButton();
        poolBPanel = new javax.swing.JPanel();
        poolControlPanel = new javax.swing.JPanel();
        poolRotRButton = new javax.swing.JButton();
        poolRotLButton = new javax.swing.JButton();
        poolSortButton = new javax.swing.JButton();
        jPanel13 = new javax.swing.JPanel();
        poolColorButton = new javax.swing.JButton();
        poolStarComboBox = new javax.swing.JComboBox<>();
        poolListPanel = new javax.swing.JPanel();
        poolListScrollPane = new javax.swing.JScrollPane();
        poolList = new javax.swing.JList<>();
        poolReadPanel = new javax.swing.JPanel();
        imageButton = new javax.swing.JButton();
        proxyButton = new javax.swing.JButton();
        piButtonPanel = new javax.swing.JPanel();
        poolWindowButton = new javax.swing.JButton();
        addButton = new javax.swing.JButton();
        invPanel = new javax.swing.JPanel();
        invTPanel = new javax.swing.JPanel();
        invNewButton = new javax.swing.JButton();
        invOpenButton = new javax.swing.JButton();
        invSaveButton = new javax.swing.JButton();
        invSaveAsButton = new javax.swing.JButton();
        fileTAPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        fileTextArea = new javax.swing.JTextArea();
        invBPanel = new javax.swing.JPanel();
        jPanel9 = new javax.swing.JPanel();
        jPanel12 = new javax.swing.JPanel();
        filterChipCountLabel = new javax.swing.JLabel();
        invSortTypeComboBox = new javax.swing.JComboBox<>();
        invSortOrderButton = new javax.swing.JButton();
        filterButton = new javax.swing.JButton();
        displayTypeButton = new javax.swing.JButton();
        invStatPanel = new javax.swing.JPanel();
        invApplyButton = new javax.swing.JButton();
        jPanel6 = new javax.swing.JPanel();
        invStarComboBox = new javax.swing.JComboBox<>();
        invColorButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        invDmgPanel = new javax.swing.JPanel();
        invDmgTextLabel = new javax.swing.JLabel();
        invDmgComboBox = new javax.swing.JComboBox<>();
        invDmgPtLabel = new javax.swing.JLabel();
        invBrkPanel = new javax.swing.JPanel();
        invBrkTextLabel = new javax.swing.JLabel();
        invBrkComboBox = new javax.swing.JComboBox<>();
        invBrkPtLabel = new javax.swing.JLabel();
        invHitPanel = new javax.swing.JPanel();
        invHitTextLabel = new javax.swing.JLabel();
        invHitComboBox = new javax.swing.JComboBox<>();
        invHitPtLabel = new javax.swing.JLabel();
        invRldPanel = new javax.swing.JPanel();
        invRldTextLabel = new javax.swing.JLabel();
        invRldComboBox = new javax.swing.JComboBox<>();
        invRldPtLabel = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        enhancementTextLabel = new javax.swing.JLabel();
        invLevelSlider = new javax.swing.JSlider();
        invLevelLabel = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        invRotLButton = new javax.swing.JButton();
        invRotRButton = new javax.swing.JButton();
        invDelButton = new javax.swing.JButton();
        invMarkCheckBox = new javax.swing.JCheckBox();
        invTagButton = new javax.swing.JButton();
        jPanel14 = new javax.swing.JPanel();
        invListPanel = new javax.swing.JPanel();
        invListScrollPane = new javax.swing.JScrollPane();
        invList = new javax.swing.JList<>();
        combLeftPanel = new javax.swing.JPanel();
        combLTPanel = new javax.swing.JPanel();
        settingButton = new javax.swing.JButton();
        jPanel8 = new javax.swing.JPanel();
        boardNameComboBox = new javax.swing.JComboBox<>();
        boardStarComboBox = new javax.swing.JComboBox<>();
        combLBPanel = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        combLabel = new javax.swing.JLabel();
        combWarningButton = new javax.swing.JButton();
        combListPanel = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        combList = new javax.swing.JList<>();
        researchButton = new javax.swing.JButton();
        combRightPanel = new javax.swing.JPanel();
        combRTPanel = new javax.swing.JPanel();
        combStopButton = new javax.swing.JButton();
        loadingLabel = new javax.swing.JLabel();
        boardImageLabel = new javax.swing.JLabel();
        showProgImageCheckBox = new javax.swing.JCheckBox();
        combStartPauseButton = new javax.swing.JButton();
        combRBPanel = new javax.swing.JPanel();
        combLabelPanel = new javax.swing.JPanel();
        combImageLabel = new javax.swing.JLabel();
        combStatPanel = new javax.swing.JPanel();
        statButton = new javax.swing.JButton();
        jPanel10 = new javax.swing.JPanel();
        combDmgTextLabel = new javax.swing.JLabel();
        combDmgPercLabel = new javax.swing.JLabel();
        combDmgResonanceStatLabel = new javax.swing.JLabel();
        combDmgPtLabel = new javax.swing.JLabel();
        combDmgStatLabel = new javax.swing.JLabel();
        jPanel11 = new javax.swing.JPanel();
        combBrkTextLabel = new javax.swing.JLabel();
        combBrkStatLabel = new javax.swing.JLabel();
        combBrkPtLabel = new javax.swing.JLabel();
        combBrkResonanceStatLabel = new javax.swing.JLabel();
        combBrkPercLabel = new javax.swing.JLabel();
        jPanel15 = new javax.swing.JPanel();
        combHitTextLabel = new javax.swing.JLabel();
        combHitStatLabel = new javax.swing.JLabel();
        combHitPtLabel = new javax.swing.JLabel();
        combHitResonanceStatLabel = new javax.swing.JLabel();
        combHitPercLabel = new javax.swing.JLabel();
        jPanel16 = new javax.swing.JPanel();
        combRldTextLabel = new javax.swing.JLabel();
        combRldStatLabel = new javax.swing.JLabel();
        combRldPtLabel = new javax.swing.JLabel();
        combRldResonanceStatLabel = new javax.swing.JLabel();
        combRldPercLabel = new javax.swing.JLabel();
        combChipListPanel = new javax.swing.JPanel();
        combChipListScrollPane = new javax.swing.JScrollPane();
        combChipList = new javax.swing.JList<>();
        jPanel18 = new javax.swing.JPanel();
        legendEquippedLabel = new javax.swing.JLabel();
        legendRotatedLabel = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        combSaveButton = new javax.swing.JButton();
        combOpenButton = new javax.swing.JButton();
        ticketLabel = new javax.swing.JLabel();
        ticketTextLabel = new javax.swing.JLabel();
        xpTextLabel = new javax.swing.JLabel();
        xpLabel = new javax.swing.JLabel();
        combMarkButton = new javax.swing.JButton();
        combTagButton = new javax.swing.JButton();
        jPanel17 = new javax.swing.JPanel();
        timeLabel = new javax.swing.JLabel();
        timeWarningButton = new javax.swing.JButton();
        combProgressBar = new javax.swing.JProgressBar();
        tipLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        poolPanel.setFocusable(false);

        poolTPanel.setFocusable(false);

        versionLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        versionLabel.setText("#.#.#");
        versionLabel.setFocusable(false);
        versionLabel.setPreferredSize(new java.awt.Dimension(50, 50));

        helpButton.setFocusable(false);
        helpButton.setMinimumSize(new java.awt.Dimension(50, 50));
        helpButton.setPreferredSize(new java.awt.Dimension(50, 50));

        displaySettingButton.setFocusable(false);
        displaySettingButton.setMinimumSize(new java.awt.Dimension(50, 50));
        displaySettingButton.setPreferredSize(new java.awt.Dimension(50, 50));

        authorButton.setFocusable(false);
        authorButton.setMinimumSize(new java.awt.Dimension(50, 50));
        authorButton.setPreferredSize(new java.awt.Dimension(25, 25));

        javax.swing.GroupLayout poolTPanelLayout = new javax.swing.GroupLayout(poolTPanel);
        poolTPanel.setLayout(poolTPanelLayout);
        poolTPanelLayout.setHorizontalGroup(
            poolTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(poolTPanelLayout.createSequentialGroup()
                .addComponent(versionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(authorButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(displaySettingButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(helpButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        poolTPanelLayout.setVerticalGroup(
            poolTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(versionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(helpButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(displaySettingButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(authorButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        poolBPanel.setFocusable(false);

        poolControlPanel.setFocusable(false);
        poolControlPanel.setPreferredSize(new java.awt.Dimension(274, 50));

        poolRotRButton.setFocusable(false);
        poolRotRButton.setMinimumSize(new java.awt.Dimension(50, 50));
        poolRotRButton.setPreferredSize(new java.awt.Dimension(50, 50));

        poolRotLButton.setFocusable(false);
        poolRotLButton.setMinimumSize(new java.awt.Dimension(50, 50));
        poolRotLButton.setPreferredSize(new java.awt.Dimension(50, 50));

        poolSortButton.setFocusable(false);
        poolSortButton.setMinimumSize(new java.awt.Dimension(50, 50));
        poolSortButton.setPreferredSize(new java.awt.Dimension(50, 50));

        poolColorButton.setFocusable(false);
        poolColorButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        poolColorButton.setPreferredSize(new java.awt.Dimension(100, 22));

        poolStarComboBox.setFocusable(false);
        poolStarComboBox.setPreferredSize(new java.awt.Dimension(100, 22));

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(poolStarComboBox, 0, 124, Short.MAX_VALUE)
            .addComponent(poolColorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addComponent(poolStarComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(poolColorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout poolControlPanelLayout = new javax.swing.GroupLayout(poolControlPanel);
        poolControlPanel.setLayout(poolControlPanelLayout);
        poolControlPanelLayout.setHorizontalGroup(
            poolControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, poolControlPanelLayout.createSequentialGroup()
                .addComponent(poolRotLButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(poolRotRButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(poolSortButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        poolControlPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {poolRotLButton, poolRotRButton, poolSortButton});

        poolControlPanelLayout.setVerticalGroup(
            poolControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(poolSortButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(poolRotLButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(poolRotRButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        poolControlPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {poolRotLButton, poolRotRButton, poolSortButton});

        poolListPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 3));
        poolListPanel.setFocusable(false);

        poolListScrollPane.setFocusable(false);
        poolListScrollPane.setPreferredSize(new java.awt.Dimension(100, 100));

        poolList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        poolList.setLayoutOrientation(javax.swing.JList.HORIZONTAL_WRAP);
        poolList.setVisibleRowCount(-1);
        poolListScrollPane.setViewportView(poolList);

        javax.swing.GroupLayout poolListPanelLayout = new javax.swing.GroupLayout(poolListPanel);
        poolListPanel.setLayout(poolListPanelLayout);
        poolListPanelLayout.setHorizontalGroup(
            poolListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(poolListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        poolListPanelLayout.setVerticalGroup(
            poolListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(poolListScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        imageButton.setFocusable(false);
        imageButton.setMinimumSize(new java.awt.Dimension(50, 50));
        imageButton.setPreferredSize(new java.awt.Dimension(50, 50));

        proxyButton.setFocusable(false);
        proxyButton.setMinimumSize(new java.awt.Dimension(50, 50));
        proxyButton.setPreferredSize(new java.awt.Dimension(50, 50));

        javax.swing.GroupLayout poolReadPanelLayout = new javax.swing.GroupLayout(poolReadPanel);
        poolReadPanel.setLayout(poolReadPanelLayout);
        poolReadPanelLayout.setHorizontalGroup(
            poolReadPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(poolReadPanelLayout.createSequentialGroup()
                .addComponent(imageButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(proxyButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        poolReadPanelLayout.setVerticalGroup(
            poolReadPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(imageButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(proxyButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        javax.swing.GroupLayout poolBPanelLayout = new javax.swing.GroupLayout(poolBPanel);
        poolBPanel.setLayout(poolBPanelLayout);
        poolBPanelLayout.setHorizontalGroup(
            poolBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(poolListPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(poolControlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(poolReadPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        poolBPanelLayout.setVerticalGroup(
            poolBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, poolBPanelLayout.createSequentialGroup()
                .addComponent(poolReadPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(8, 8, 8)
                .addComponent(poolListPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(poolControlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout poolPanelLayout = new javax.swing.GroupLayout(poolPanel);
        poolPanel.setLayout(poolPanelLayout);
        poolPanelLayout.setHorizontalGroup(
            poolPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(poolBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(poolTPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        poolPanelLayout.setVerticalGroup(
            poolPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(poolPanelLayout.createSequentialGroup()
                .addComponent(poolTPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(poolBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        piButtonPanel.setFocusable(false);

        poolWindowButton.setFocusable(false);
        poolWindowButton.setMinimumSize(new java.awt.Dimension(50, 50));
        poolWindowButton.setPreferredSize(new java.awt.Dimension(50, 50));

        addButton.setEnabled(false);
        addButton.setFocusable(false);
        addButton.setMinimumSize(new java.awt.Dimension(50, 50));
        addButton.setPreferredSize(new java.awt.Dimension(50, 50));

        javax.swing.GroupLayout piButtonPanelLayout = new javax.swing.GroupLayout(piButtonPanel);
        piButtonPanel.setLayout(piButtonPanelLayout);
        piButtonPanelLayout.setHorizontalGroup(
            piButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(poolWindowButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(addButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        piButtonPanelLayout.setVerticalGroup(
            piButtonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(piButtonPanelLayout.createSequentialGroup()
                .addComponent(poolWindowButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(addButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        invPanel.setFocusable(false);

        invTPanel.setFocusable(false);

        invNewButton.setFocusable(false);
        invNewButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invNewButton.setPreferredSize(new java.awt.Dimension(50, 50));

        invOpenButton.setFocusable(false);
        invOpenButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invOpenButton.setPreferredSize(new java.awt.Dimension(50, 50));

        invSaveButton.setEnabled(false);
        invSaveButton.setFocusable(false);
        invSaveButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invSaveButton.setPreferredSize(new java.awt.Dimension(50, 50));

        invSaveAsButton.setFocusable(false);
        invSaveAsButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invSaveAsButton.setPreferredSize(new java.awt.Dimension(50, 50));

        fileTAPanel.setFocusable(false);
        fileTAPanel.setPreferredSize(new java.awt.Dimension(50, 50));

        jScrollPane1.setBorder(null);
        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.setToolTipText("");
        jScrollPane1.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        jScrollPane1.setFocusable(false);

        fileTextArea.setBackground(java.awt.SystemColor.control);
        fileTextArea.setColumns(20);
        fileTextArea.setLineWrap(true);
        fileTextArea.setRows(5);
        fileTextArea.setEnabled(false);
        fileTextArea.setFocusable(false);
        jScrollPane1.setViewportView(fileTextArea);

        javax.swing.GroupLayout fileTAPanelLayout = new javax.swing.GroupLayout(fileTAPanel);
        fileTAPanel.setLayout(fileTAPanelLayout);
        fileTAPanelLayout.setHorizontalGroup(
            fileTAPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );
        fileTAPanelLayout.setVerticalGroup(
            fileTAPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout invTPanelLayout = new javax.swing.GroupLayout(invTPanel);
        invTPanel.setLayout(invTPanelLayout);
        invTPanelLayout.setHorizontalGroup(
            invTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(invTPanelLayout.createSequentialGroup()
                .addComponent(invNewButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(invOpenButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(invSaveButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(invSaveAsButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fileTAPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 82, Short.MAX_VALUE))
        );
        invTPanelLayout.setVerticalGroup(
            invTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(invNewButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(invOpenButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(invSaveButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(invSaveAsButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(fileTAPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        invBPanel.setFocusable(false);

        jPanel9.setFocusable(false);

        jPanel12.setFocusable(false);

        filterChipCountLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        filterChipCountLabel.setFocusable(false);

        invSortTypeComboBox.setFocusable(false);

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(filterChipCountLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(invSortTypeComboBox, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(invSortTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(filterChipCountLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        invSortOrderButton.setEnabled(false);
        invSortOrderButton.setFocusable(false);
        invSortOrderButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invSortOrderButton.setPreferredSize(new java.awt.Dimension(50, 50));

        filterButton.setFocusable(false);
        filterButton.setMinimumSize(new java.awt.Dimension(50, 50));
        filterButton.setPreferredSize(new java.awt.Dimension(50, 50));

        displayTypeButton.setFocusable(false);
        displayTypeButton.setMinimumSize(new java.awt.Dimension(50, 50));
        displayTypeButton.setPreferredSize(new java.awt.Dimension(50, 50));

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(filterButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(invSortOrderButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(displayTypeButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(displayTypeButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(filterButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(invSortOrderButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        invStatPanel.setFocusable(false);

        invApplyButton.setText("apply all");
        invApplyButton.setFocusable(false);

        invStarComboBox.setEnabled(false);
        invStarComboBox.setFocusable(false);
        invStarComboBox.setPreferredSize(new java.awt.Dimension(100, 22));

        invColorButton.setEnabled(false);
        invColorButton.setFocusable(false);
        invColorButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        invColorButton.setPreferredSize(new java.awt.Dimension(75, 22));

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(invStarComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invColorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(invStarComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(invColorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );

        jPanel2.setFocusable(false);

        invDmgPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 3));
        invDmgPanel.setLayout(new java.awt.BorderLayout(5, 0));

        invDmgTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        invDmgTextLabel.setText("D");
        invDmgTextLabel.setFocusable(false);
        invDmgTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        invDmgPanel.add(invDmgTextLabel, java.awt.BorderLayout.LINE_START);

        invDmgComboBox.setEnabled(false);
        invDmgComboBox.setFocusable(false);
        invDmgComboBox.setPreferredSize(new java.awt.Dimension(50, 22));
        invDmgPanel.add(invDmgComboBox, java.awt.BorderLayout.CENTER);

        invDmgPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        invDmgPtLabel.setText("-");
        invDmgPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        invDmgPtLabel.setFocusable(false);
        invDmgPtLabel.setPreferredSize(new java.awt.Dimension(22, 22));
        invDmgPanel.add(invDmgPtLabel, java.awt.BorderLayout.LINE_END);

        invBrkPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 3));
        invBrkPanel.setLayout(new java.awt.BorderLayout(5, 0));

        invBrkTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        invBrkTextLabel.setText("B");
        invBrkTextLabel.setFocusable(false);
        invBrkTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        invBrkPanel.add(invBrkTextLabel, java.awt.BorderLayout.LINE_START);

        invBrkComboBox.setEnabled(false);
        invBrkComboBox.setFocusable(false);
        invBrkComboBox.setPreferredSize(new java.awt.Dimension(50, 22));
        invBrkPanel.add(invBrkComboBox, java.awt.BorderLayout.CENTER);

        invBrkPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        invBrkPtLabel.setText("-");
        invBrkPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        invBrkPtLabel.setFocusable(false);
        invBrkPtLabel.setPreferredSize(new java.awt.Dimension(22, 22));
        invBrkPanel.add(invBrkPtLabel, java.awt.BorderLayout.LINE_END);

        invHitPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 3));
        invHitPanel.setLayout(new java.awt.BorderLayout(5, 0));

        invHitTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        invHitTextLabel.setText("H");
        invHitTextLabel.setFocusable(false);
        invHitTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        invHitPanel.add(invHitTextLabel, java.awt.BorderLayout.LINE_START);

        invHitComboBox.setEnabled(false);
        invHitComboBox.setFocusable(false);
        invHitComboBox.setPreferredSize(new java.awt.Dimension(50, 22));
        invHitPanel.add(invHitComboBox, java.awt.BorderLayout.CENTER);

        invHitPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        invHitPtLabel.setText("-");
        invHitPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        invHitPtLabel.setFocusable(false);
        invHitPtLabel.setPreferredSize(new java.awt.Dimension(22, 22));
        invHitPanel.add(invHitPtLabel, java.awt.BorderLayout.LINE_END);

        invRldPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 3));
        invRldPanel.setLayout(new java.awt.BorderLayout(5, 0));

        invRldTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        invRldTextLabel.setText("R");
        invRldTextLabel.setFocusable(false);
        invRldTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        invRldPanel.add(invRldTextLabel, java.awt.BorderLayout.LINE_START);

        invRldComboBox.setEnabled(false);
        invRldComboBox.setFocusable(false);
        invRldComboBox.setPreferredSize(new java.awt.Dimension(50, 22));
        invRldPanel.add(invRldComboBox, java.awt.BorderLayout.CENTER);

        invRldPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        invRldPtLabel.setText("-");
        invRldPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        invRldPtLabel.setFocusable(false);
        invRldPtLabel.setPreferredSize(new java.awt.Dimension(22, 22));
        invRldPanel.add(invRldPtLabel, java.awt.BorderLayout.LINE_END);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(invDmgPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(invBrkPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(invHitPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(invRldPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(invDmgPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(invBrkPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(invHitPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 1, Short.MAX_VALUE)
                .addComponent(invRldPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        enhancementTextLabel.setText("강화");
        enhancementTextLabel.setFocusable(false);

        invLevelSlider.setMajorTickSpacing(5);
        invLevelSlider.setMaximum(20);
        invLevelSlider.setMinorTickSpacing(1);
        invLevelSlider.setSnapToTicks(true);
        invLevelSlider.setValue(0);
        invLevelSlider.setEnabled(false);
        invLevelSlider.setFocusable(false);
        invLevelSlider.setPreferredSize(new java.awt.Dimension(100, 22));

        invLevelLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        invLevelLabel.setText("-");
        invLevelLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        invLevelLabel.setFocusable(false);
        invLevelLabel.setPreferredSize(new java.awt.Dimension(22, 22));

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addComponent(enhancementTextLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invLevelSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invLevelLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                .addComponent(enhancementTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(invLevelSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(invLevelLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        invRotLButton.setEnabled(false);
        invRotLButton.setFocusable(false);
        invRotLButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invRotLButton.setPreferredSize(new java.awt.Dimension(50, 50));

        invRotRButton.setEnabled(false);
        invRotRButton.setFocusable(false);
        invRotRButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invRotRButton.setPreferredSize(new java.awt.Dimension(50, 50));

        invDelButton.setEnabled(false);
        invDelButton.setFocusable(false);
        invDelButton.setMinimumSize(new java.awt.Dimension(50, 50));
        invDelButton.setPreferredSize(new java.awt.Dimension(50, 50));

        invMarkCheckBox.setText("mark");
        invMarkCheckBox.setBorder(null);
        invMarkCheckBox.setEnabled(false);
        invMarkCheckBox.setFocusable(false);
        invMarkCheckBox.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        invMarkCheckBox.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        invMarkCheckBox.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(invRotLButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(invRotRButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(invDelButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invMarkCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel7Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {invDelButton, invRotLButton, invRotRButton});

        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                .addComponent(invDelButton, javax.swing.GroupLayout.DEFAULT_SIZE, 51, Short.MAX_VALUE)
                .addComponent(invMarkCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(invRotRButton, javax.swing.GroupLayout.DEFAULT_SIZE, 51, Short.MAX_VALUE)
                .addComponent(invRotLButton, javax.swing.GroupLayout.DEFAULT_SIZE, 51, Short.MAX_VALUE))
        );

        jPanel7Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {invDelButton, invRotLButton, invRotRButton});

        invTagButton.setText("tag");
        invTagButton.setEnabled(false);
        invTagButton.setFocusable(false);
        invTagButton.setMargin(new java.awt.Insets(2, 2, 2, 2));

        javax.swing.GroupLayout invStatPanelLayout = new javax.swing.GroupLayout(invStatPanel);
        invStatPanel.setLayout(invStatPanelLayout);
        invStatPanelLayout.setHorizontalGroup(
            invStatPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(invStatPanelLayout.createSequentialGroup()
                .addGroup(invStatPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addComponent(invApplyButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(invTagButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        invStatPanelLayout.setVerticalGroup(
            invStatPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(invStatPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(invStatPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(invStatPanelLayout.createSequentialGroup()
                        .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invTagButton)
                .addGap(0, 0, 0)
                .addComponent(invApplyButton))
        );

        invListPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 3));
        invListPanel.setFocusable(false);

        invListScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        invListScrollPane.setFocusable(false);
        invListScrollPane.setPreferredSize(new java.awt.Dimension(100, 100));

        invList.setDragEnabled(true);
        invList.setDropMode(javax.swing.DropMode.INSERT);
        invList.setLayoutOrientation(javax.swing.JList.HORIZONTAL_WRAP);
        invList.setVisibleRowCount(-1);
        invListScrollPane.setViewportView(invList);

        javax.swing.GroupLayout invListPanelLayout = new javax.swing.GroupLayout(invListPanel);
        invListPanel.setLayout(invListPanelLayout);
        invListPanelLayout.setHorizontalGroup(
            invListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(invListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        invListPanelLayout.setVerticalGroup(
            invListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(invListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(invListPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(invListPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout invBPanelLayout = new javax.swing.GroupLayout(invBPanel);
        invBPanel.setLayout(invBPanelLayout);
        invBPanelLayout.setHorizontalGroup(
            invBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(invStatPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        invBPanelLayout.setVerticalGroup(
            invBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(invBPanelLayout.createSequentialGroup()
                .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invStatPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout invPanelLayout = new javax.swing.GroupLayout(invPanel);
        invPanel.setLayout(invPanelLayout);
        invPanelLayout.setHorizontalGroup(
            invPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(invTPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(invBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        invPanelLayout.setVerticalGroup(
            invPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(invPanelLayout.createSequentialGroup()
                .addComponent(invTPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        combLeftPanel.setFocusable(false);

        combLTPanel.setFocusable(false);

        settingButton.setFocusable(false);
        settingButton.setMinimumSize(new java.awt.Dimension(50, 50));
        settingButton.setPreferredSize(new java.awt.Dimension(50, 50));

        jPanel8.setFocusable(false);
        jPanel8.setPreferredSize(new java.awt.Dimension(100, 50));

        boardNameComboBox.setFocusable(false);
        boardNameComboBox.setPreferredSize(new java.awt.Dimension(100, 21));

        boardStarComboBox.setFocusable(false);
        boardStarComboBox.setPreferredSize(new java.awt.Dimension(100, 21));

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(boardNameComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(boardStarComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addComponent(boardNameComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(boardStarComboBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout combLTPanelLayout = new javax.swing.GroupLayout(combLTPanel);
        combLTPanel.setLayout(combLTPanelLayout);
        combLTPanelLayout.setHorizontalGroup(
            combLTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combLTPanelLayout.createSequentialGroup()
                .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(settingButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        combLTPanelLayout.setVerticalGroup(
            combLTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel8, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(settingButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        combLBPanel.setFocusable(false);

        jPanel4.setFocusable(false);
        jPanel4.setLayout(new java.awt.BorderLayout());

        combLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combLabel.setText("0");
        combLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combLabel.setFocusable(false);
        combLabel.setPreferredSize(new java.awt.Dimension(100, 22));
        jPanel4.add(combLabel, java.awt.BorderLayout.CENTER);

        combWarningButton.setFocusable(false);
        combWarningButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        combWarningButton.setPreferredSize(new java.awt.Dimension(21, 21));
        jPanel4.add(combWarningButton, java.awt.BorderLayout.WEST);

        combListPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 3));
        combListPanel.setFocusable(false);

        jScrollPane4.setFocusable(false);
        jScrollPane4.setPreferredSize(new java.awt.Dimension(100, 100));

        combList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        combList.setVisibleRowCount(-1);
        jScrollPane4.setViewportView(combList);

        javax.swing.GroupLayout combListPanelLayout = new javax.swing.GroupLayout(combListPanel);
        combListPanel.setLayout(combListPanelLayout);
        combListPanelLayout.setHorizontalGroup(
            combListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combListPanelLayout.setVerticalGroup(
            combListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        researchButton.setText("research");

        javax.swing.GroupLayout combLBPanelLayout = new javax.swing.GroupLayout(combLBPanel);
        combLBPanel.setLayout(combLBPanelLayout);
        combLBPanelLayout.setHorizontalGroup(
            combLBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combListPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(researchButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combLBPanelLayout.setVerticalGroup(
            combLBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, combLBPanelLayout.createSequentialGroup()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(combListPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(researchButton))
        );

        javax.swing.GroupLayout combLeftPanelLayout = new javax.swing.GroupLayout(combLeftPanel);
        combLeftPanel.setLayout(combLeftPanelLayout);
        combLeftPanelLayout.setHorizontalGroup(
            combLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combLTPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(combLBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combLeftPanelLayout.setVerticalGroup(
            combLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combLeftPanelLayout.createSequentialGroup()
                .addComponent(combLTPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(combLBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        combRightPanel.setFocusable(false);

        combRTPanel.setFocusable(false);
        combRTPanel.setPreferredSize(new java.awt.Dimension(300, 50));

        combStopButton.setFocusable(false);
        combStopButton.setMinimumSize(new java.awt.Dimension(50, 50));
        combStopButton.setPreferredSize(new java.awt.Dimension(50, 50));

        loadingLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        loadingLabel.setFocusable(false);
        loadingLabel.setPreferredSize(new java.awt.Dimension(50, 50));

        boardImageLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        boardImageLabel.setFocusable(false);
        boardImageLabel.setPreferredSize(new java.awt.Dimension(50, 50));

        showProgImageCheckBox.setFocusable(false);

        combStartPauseButton.setFocusable(false);
        combStartPauseButton.setMinimumSize(new java.awt.Dimension(50, 50));
        combStartPauseButton.setPreferredSize(new java.awt.Dimension(50, 50));

        javax.swing.GroupLayout combRTPanelLayout = new javax.swing.GroupLayout(combRTPanel);
        combRTPanel.setLayout(combRTPanelLayout);
        combRTPanelLayout.setHorizontalGroup(
            combRTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combRTPanelLayout.createSequentialGroup()
                .addComponent(showProgImageCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(boardImageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(combStartPauseButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(combStopButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(loadingLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        combRTPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {boardImageLabel, loadingLabel});

        combRTPanelLayout.setVerticalGroup(
            combRTPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combStopButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(loadingLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(showProgImageCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(boardImageLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(combStartPauseButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        combRBPanel.setFocusable(false);

        combLabelPanel.setFocusable(false);
        combLabelPanel.setPreferredSize(new java.awt.Dimension(100, 100));

        combImageLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combImageLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combImageLabel.setFocusable(false);

        javax.swing.GroupLayout combLabelPanelLayout = new javax.swing.GroupLayout(combLabelPanel);
        combLabelPanel.setLayout(combLabelPanelLayout);
        combLabelPanelLayout.setHorizontalGroup(
            combLabelPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combImageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combLabelPanelLayout.setVerticalGroup(
            combLabelPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combImageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        combStatPanel.setFocusable(false);

        statButton.setText("detail");
        statButton.setEnabled(false);
        statButton.setFocusable(false);

        combDmgTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combDmgTextLabel.setText("D");
        combDmgTextLabel.setFocusable(false);
        combDmgTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        combDmgTextLabel.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        combDmgPercLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combDmgPercLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combDmgPercLabel.setFocusable(false);
        combDmgPercLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        combDmgResonanceStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combDmgResonanceStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combDmgResonanceStatLabel.setFocusable(false);
        combDmgResonanceStatLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combDmgPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combDmgPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combDmgPtLabel.setFocusable(false);
        combDmgPtLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combDmgStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combDmgStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combDmgStatLabel.setFocusable(false);
        combDmgStatLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addComponent(combDmgTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combDmgStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combDmgPercLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combDmgResonanceStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combDmgPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combDmgPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combDmgStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combDmgPercLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combDmgResonanceStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(combDmgTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        combBrkTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combBrkTextLabel.setText("B");
        combBrkTextLabel.setFocusable(false);
        combBrkTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        combBrkTextLabel.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        combBrkStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combBrkStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combBrkStatLabel.setFocusable(false);
        combBrkStatLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        combBrkPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combBrkPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combBrkPtLabel.setFocusable(false);
        combBrkPtLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combBrkResonanceStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combBrkResonanceStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combBrkResonanceStatLabel.setFocusable(false);
        combBrkResonanceStatLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combBrkPercLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combBrkPercLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combBrkPercLabel.setFocusable(false);
        combBrkPercLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(combBrkTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combBrkStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combBrkPercLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combBrkResonanceStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combBrkPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combBrkStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combBrkPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combBrkPercLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combBrkResonanceStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(combBrkTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        combHitTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combHitTextLabel.setText("H");
        combHitTextLabel.setFocusable(false);
        combHitTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        combHitTextLabel.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        combHitStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combHitStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combHitStatLabel.setFocusable(false);
        combHitStatLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        combHitPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combHitPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combHitPtLabel.setFocusable(false);
        combHitPtLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combHitResonanceStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combHitResonanceStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combHitResonanceStatLabel.setFocusable(false);
        combHitResonanceStatLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combHitPercLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combHitPercLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combHitPercLabel.setFocusable(false);
        combHitPercLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        javax.swing.GroupLayout jPanel15Layout = new javax.swing.GroupLayout(jPanel15);
        jPanel15.setLayout(jPanel15Layout);
        jPanel15Layout.setHorizontalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addComponent(combHitTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combHitStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combHitPercLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combHitResonanceStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combHitPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel15Layout.setVerticalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel15Layout.createSequentialGroup()
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combHitStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combHitPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combHitPercLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combHitResonanceStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(combHitTextLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        combRldTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combRldTextLabel.setText("R");
        combRldTextLabel.setFocusable(false);
        combRldTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        combRldTextLabel.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        combRldStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combRldStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combRldStatLabel.setFocusable(false);
        combRldStatLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        combRldPtLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combRldPtLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combRldPtLabel.setFocusable(false);
        combRldPtLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combRldResonanceStatLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combRldResonanceStatLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combRldResonanceStatLabel.setFocusable(false);
        combRldResonanceStatLabel.setPreferredSize(new java.awt.Dimension(50, 22));

        combRldPercLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        combRldPercLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        combRldPercLabel.setFocusable(false);
        combRldPercLabel.setPreferredSize(new java.awt.Dimension(110, 22));

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addComponent(combRldTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combRldStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combRldPercLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combRldResonanceStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combRldPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combRldPtLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combRldStatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combRldPercLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combRldResonanceStatLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(combRldTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout combStatPanelLayout = new javax.swing.GroupLayout(combStatPanel);
        combStatPanel.setLayout(combStatPanelLayout);
        combStatPanelLayout.setHorizontalGroup(
            combStatPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel15, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(statButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combStatPanelLayout.setVerticalGroup(
            combStatPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combStatPanelLayout.createSequentialGroup()
                .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel15, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statButton))
        );

        combChipListPanel.setFocusable(false);

        combChipListScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        combChipListScrollPane.setFocusable(false);
        combChipListScrollPane.setPreferredSize(new java.awt.Dimension(100, 100));

        combChipList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        combChipList.setFocusable(false);
        combChipList.setLayoutOrientation(javax.swing.JList.HORIZONTAL_WRAP);
        combChipList.setVisibleRowCount(-1);
        combChipListScrollPane.setViewportView(combChipList);

        jPanel18.setLayout(new java.awt.BorderLayout());

        legendEquippedLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        legendEquippedLabel.setText("legend equipped");
        jPanel18.add(legendEquippedLabel, java.awt.BorderLayout.CENTER);

        legendRotatedLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        legendRotatedLabel.setText("legend rotated");
        jPanel18.add(legendRotatedLabel, java.awt.BorderLayout.SOUTH);

        javax.swing.GroupLayout combChipListPanelLayout = new javax.swing.GroupLayout(combChipListPanel);
        combChipListPanel.setLayout(combChipListPanelLayout);
        combChipListPanelLayout.setHorizontalGroup(
            combChipListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combChipListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel18, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combChipListPanelLayout.setVerticalGroup(
            combChipListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combChipListPanelLayout.createSequentialGroup()
                .addComponent(combChipListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jPanel3.setFocusable(false);

        combSaveButton.setEnabled(false);
        combSaveButton.setFocusable(false);
        combSaveButton.setMinimumSize(new java.awt.Dimension(50, 50));
        combSaveButton.setPreferredSize(new java.awt.Dimension(50, 50));

        combOpenButton.setFocusable(false);
        combOpenButton.setMinimumSize(new java.awt.Dimension(50, 50));
        combOpenButton.setPreferredSize(new java.awt.Dimension(50, 50));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(combOpenButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(combSaveButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combOpenButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(combSaveButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        ticketLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        ticketLabel.setText("-");
        ticketLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        ticketLabel.setFocusable(false);
        ticketLabel.setPreferredSize(new java.awt.Dimension(75, 22));

        ticketTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        ticketTextLabel.setText("ticket");
        ticketTextLabel.setFocusable(false);
        ticketTextLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        xpTextLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        xpTextLabel.setText("enh");
        xpTextLabel.setFocusable(false);

        xpLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        xpLabel.setText("-");
        xpLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        xpLabel.setFocusable(false);
        xpLabel.setPreferredSize(new java.awt.Dimension(75, 22));

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(ticketTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(xpTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(xpLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ticketLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ticketTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(ticketLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(xpLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(xpTextLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jPanel3Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {ticketLabel, xpLabel});

        combMarkButton.setText("mark");
        combMarkButton.setEnabled(false);
        combMarkButton.setFocusable(false);
        combMarkButton.setMargin(new java.awt.Insets(2, 2, 2, 2));

        combTagButton.setText("tag");
        combTagButton.setEnabled(false);
        combTagButton.setFocusable(false);
        combTagButton.setMargin(new java.awt.Insets(2, 2, 2, 2));

        jPanel17.setLayout(new java.awt.BorderLayout());

        timeLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        timeLabel.setText("0:00:00");
        timeLabel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        timeLabel.setFocusable(false);
        timeLabel.setPreferredSize(new java.awt.Dimension(100, 22));
        jPanel17.add(timeLabel, java.awt.BorderLayout.CENTER);

        timeWarningButton.setPreferredSize(new java.awt.Dimension(21, 21));
        jPanel17.add(timeWarningButton, java.awt.BorderLayout.WEST);

        javax.swing.GroupLayout combRBPanelLayout = new javax.swing.GroupLayout(combRBPanel);
        combRBPanel.setLayout(combRBPanelLayout);
        combRBPanelLayout.setHorizontalGroup(
            combRBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combRBPanelLayout.createSequentialGroup()
                .addGroup(combRBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(combStatPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combLabelPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 175, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(combRBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(combChipListPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combMarkButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combTagButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(jPanel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combRBPanelLayout.setVerticalGroup(
            combRBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combRBPanelLayout.createSequentialGroup()
                .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(combRBPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(combRBPanelLayout.createSequentialGroup()
                        .addComponent(combLabelPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(combStatPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(combRBPanelLayout.createSequentialGroup()
                        .addComponent(combChipListPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(combMarkButton)
                        .addGap(0, 0, 0)
                        .addComponent(combTagButton))))
        );

        javax.swing.GroupLayout combRightPanelLayout = new javax.swing.GroupLayout(combRightPanel);
        combRightPanel.setLayout(combRightPanelLayout);
        combRightPanelLayout.setHorizontalGroup(
            combRightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(combRTPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(combRBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        combRightPanelLayout.setVerticalGroup(
            combRightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(combRightPanelLayout.createSequentialGroup()
                .addComponent(combRTPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(combRBPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        combProgressBar.setFocusable(false);

        tipLabel.setText(" ");
        tipLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED), javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(poolPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(piButtonPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(invPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(combLeftPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(combRightPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addComponent(combProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(tipLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(invPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combLeftPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(combRightPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(piButtonPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(poolPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(combProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(tipLabel))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (invFile_confirmSave()) {
            process_stop();
            invLCR.stopTimer();
            dispose();
            System.exit(0);
        }
    }//GEN-LAST:event_formWindowClosing

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JButton authorButton;
    private javax.swing.JLabel boardImageLabel;
    private javax.swing.JComboBox<String> boardNameComboBox;
    private javax.swing.JComboBox<String> boardStarComboBox;
    private javax.swing.JLabel combBrkPercLabel;
    private javax.swing.JLabel combBrkPtLabel;
    private javax.swing.JLabel combBrkResonanceStatLabel;
    private javax.swing.JLabel combBrkStatLabel;
    private javax.swing.JLabel combBrkTextLabel;
    private javax.swing.JList<Chip> combChipList;
    private javax.swing.JPanel combChipListPanel;
    private javax.swing.JScrollPane combChipListScrollPane;
    private javax.swing.JLabel combDmgPercLabel;
    private javax.swing.JLabel combDmgPtLabel;
    private javax.swing.JLabel combDmgResonanceStatLabel;
    private javax.swing.JLabel combDmgStatLabel;
    private javax.swing.JLabel combDmgTextLabel;
    private javax.swing.JLabel combHitPercLabel;
    private javax.swing.JLabel combHitPtLabel;
    private javax.swing.JLabel combHitResonanceStatLabel;
    private javax.swing.JLabel combHitStatLabel;
    private javax.swing.JLabel combHitTextLabel;
    private javax.swing.JLabel combImageLabel;
    private javax.swing.JPanel combLBPanel;
    private javax.swing.JPanel combLTPanel;
    private javax.swing.JLabel combLabel;
    private javax.swing.JPanel combLabelPanel;
    private javax.swing.JPanel combLeftPanel;
    private javax.swing.JList<Board> combList;
    private javax.swing.JPanel combListPanel;
    private javax.swing.JButton combMarkButton;
    private javax.swing.JButton combOpenButton;
    private javax.swing.JProgressBar combProgressBar;
    private javax.swing.JPanel combRBPanel;
    private javax.swing.JPanel combRTPanel;
    private javax.swing.JPanel combRightPanel;
    private javax.swing.JLabel combRldPercLabel;
    private javax.swing.JLabel combRldPtLabel;
    private javax.swing.JLabel combRldResonanceStatLabel;
    private javax.swing.JLabel combRldStatLabel;
    private javax.swing.JLabel combRldTextLabel;
    private javax.swing.JButton combSaveButton;
    private javax.swing.JButton combStartPauseButton;
    private javax.swing.JPanel combStatPanel;
    private javax.swing.JButton combStopButton;
    private javax.swing.JButton combTagButton;
    private javax.swing.JButton combWarningButton;
    private javax.swing.JButton displaySettingButton;
    private javax.swing.JButton displayTypeButton;
    private javax.swing.JLabel enhancementTextLabel;
    private javax.swing.JPanel fileTAPanel;
    private javax.swing.JTextArea fileTextArea;
    private javax.swing.JButton filterButton;
    private javax.swing.JLabel filterChipCountLabel;
    private javax.swing.JButton helpButton;
    private javax.swing.JButton imageButton;
    private javax.swing.JButton invApplyButton;
    private javax.swing.JPanel invBPanel;
    private javax.swing.JComboBox<String> invBrkComboBox;
    private javax.swing.JPanel invBrkPanel;
    private javax.swing.JLabel invBrkPtLabel;
    private javax.swing.JLabel invBrkTextLabel;
    private javax.swing.JButton invColorButton;
    private javax.swing.JButton invDelButton;
    private javax.swing.JComboBox<String> invDmgComboBox;
    private javax.swing.JPanel invDmgPanel;
    private javax.swing.JLabel invDmgPtLabel;
    private javax.swing.JLabel invDmgTextLabel;
    private javax.swing.JComboBox<String> invHitComboBox;
    private javax.swing.JPanel invHitPanel;
    private javax.swing.JLabel invHitPtLabel;
    private javax.swing.JLabel invHitTextLabel;
    private javax.swing.JLabel invLevelLabel;
    private javax.swing.JSlider invLevelSlider;
    private javax.swing.JList<Chip> invList;
    private javax.swing.JPanel invListPanel;
    private javax.swing.JScrollPane invListScrollPane;
    private javax.swing.JCheckBox invMarkCheckBox;
    private javax.swing.JButton invNewButton;
    private javax.swing.JButton invOpenButton;
    private javax.swing.JPanel invPanel;
    private javax.swing.JComboBox<String> invRldComboBox;
    private javax.swing.JPanel invRldPanel;
    private javax.swing.JLabel invRldPtLabel;
    private javax.swing.JLabel invRldTextLabel;
    private javax.swing.JButton invRotLButton;
    private javax.swing.JButton invRotRButton;
    private javax.swing.JButton invSaveAsButton;
    private javax.swing.JButton invSaveButton;
    private javax.swing.JButton invSortOrderButton;
    private javax.swing.JComboBox<String> invSortTypeComboBox;
    private javax.swing.JComboBox<String> invStarComboBox;
    private javax.swing.JPanel invStatPanel;
    private javax.swing.JPanel invTPanel;
    private javax.swing.JButton invTagButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JLabel legendEquippedLabel;
    private javax.swing.JLabel legendRotatedLabel;
    private javax.swing.JLabel loadingLabel;
    private javax.swing.JPanel piButtonPanel;
    private javax.swing.JPanel poolBPanel;
    private javax.swing.JButton poolColorButton;
    private javax.swing.JPanel poolControlPanel;
    private javax.swing.JList<Chip> poolList;
    private javax.swing.JPanel poolListPanel;
    private javax.swing.JScrollPane poolListScrollPane;
    private javax.swing.JPanel poolPanel;
    private javax.swing.JPanel poolReadPanel;
    private javax.swing.JButton poolRotLButton;
    private javax.swing.JButton poolRotRButton;
    private javax.swing.JButton poolSortButton;
    private javax.swing.JComboBox<String> poolStarComboBox;
    private javax.swing.JPanel poolTPanel;
    private javax.swing.JButton poolWindowButton;
    private javax.swing.JButton proxyButton;
    private javax.swing.JButton researchButton;
    private javax.swing.JButton settingButton;
    private javax.swing.JCheckBox showProgImageCheckBox;
    private javax.swing.JButton statButton;
    private javax.swing.JLabel ticketLabel;
    private javax.swing.JLabel ticketTextLabel;
    private javax.swing.JLabel timeLabel;
    private javax.swing.JButton timeWarningButton;
    private javax.swing.JLabel tipLabel;
    private javax.swing.JLabel versionLabel;
    private javax.swing.JLabel xpLabel;
    private javax.swing.JLabel xpTextLabel;
    // End of variables declaration//GEN-END:variables
}