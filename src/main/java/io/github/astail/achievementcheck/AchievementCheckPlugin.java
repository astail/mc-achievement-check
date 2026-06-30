package io.github.astail.achievementcheck;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AchievementCheck 本体。
 *
 * <p>各プレイヤーが {@code /ac} で、自分のバニラ実績の達成状況と「不足している条件」（未訪問バイオーム等）を
 * 確認できるようにする。確認系のみで進捗の改変は行わず、サーバー側だけで動作する（クライアント MOD 不要）。
 * GUI のクリックを読み取り専用にするため、自身を {@link Listener} として登録する。</p>
 */
public final class AchievementCheckPlugin extends JavaPlugin implements Listener {

    private boolean active;
    private boolean showCompleted;
    private int hoverMissingLimit;
    private int maxList;
    private int guiRows;

    private AdvancementReader reader;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        reader = new AdvancementReader(this);
        if (!register()) {
            // コマンド登録に失敗した場合は disablePlugin 済み。離脱する。
            return;
        }
        getLogger().info("AchievementCheck を有効化しました（状態: " + (active ? "ON" : "OFF")
                + " / 達成済み表示: " + (showCompleted ? "あり" : "なし") + "）。");
    }

    @Override
    public void onDisable() {
        // 開いている自前 GUI を閉じる（読み取り専用リスナーが外れた後に操作されないように）。
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AchievementGui) {
                player.closeInventory();
            }
        }
    }

    /** plugin.yml のコマンドとリスナーを登録する。成功で true、コマンド未定義で無効化した場合 false。 */
    private boolean register() {
        PluginCommand command = getCommand("achievementcheck");
        if (command == null) {
            getLogger().severe("plugin.yml に achievementcheck コマンドが定義されていません。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        AchievementCheckCommand handler = new AchievementCheckCommand(this, reader);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getPluginManager().registerEvents(this, this);
        return true;
    }

    /** config.yml を読み直して設定値を反映する。 */
    private void loadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();
        active = config.getBoolean("enabled", true);
        showCompleted = config.getBoolean("show-completed", true);
        hoverMissingLimit = Math.max(1, config.getInt("hover-missing-limit", 10));
        maxList = Math.max(1, config.getInt("max-list", 80));
        guiRows = Math.min(6, Math.max(2, config.getInt("gui-rows", 6)));
    }

    // ───────────────────────── GUI クリック処理（読み取り専用） ─────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AchievementGui gui)) {
            return;
        }
        event.setCancelled(true); // 読み取り専用 GUI。アイテムの移動・取得を一切させない。
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // クリックされたのが GUI 上段（自分の Inventory）のときだけナビ処理する。
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != gui) {
            return;
        }
        gui.handleClick(event.getRawSlot(), player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AchievementGui) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── 公開ヘルパー ─────────────────────────

    /** config を再読み込みして設定値を反映する。 */
    public void reloadAll() {
        loadSettings();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isShowCompleted() {
        return showCompleted;
    }

    public int getHoverMissingLimit() {
        return hoverMissingLimit;
    }

    public int getMaxList() {
        return maxList;
    }

    public int getGuiRows() {
        return guiRows;
    }

    public AdvancementReader getReader() {
        return reader;
    }
}
