package io.github.astail.achievementcheck;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 実績確認 GUI（チェスト型インベントリ）。
 *
 * <p>一覧画面ではコレクション系実績をアイコンで並べ、カーソルを合わせると説明（lore）に進捗と
 * 未達成の条件が出る。アイコンをクリックすると、その実績の全条件をページ送りで表示する詳細画面に移る。
 * すべて読み取り専用で、クリックはナビゲーション以外キャンセルされる。</p>
 *
 * <p>ナビゲーション・ページ状態を保持するため、画面遷移のたびに新しいインスタンス（＝新しい
 * {@link Inventory}）を生成して開き直す。{@link InventoryHolder} なので
 * {@link AchievementCheckPlugin} のクリックリスナーから自分の GUI だと識別できる。</p>
 */
public final class AchievementGui implements InventoryHolder {

    private enum Mode { LIST, DETAIL }

    private final AchievementCheckPlugin plugin;
    private final AdvancementReader reader;
    private final Mode mode;
    private final Advancement detailAdvancement; // DETAIL のみ使用
    private final int listReturnPage;            // DETAIL から「戻る」一覧ページ

    private Inventory inventory;
    private int page;
    private int pageCount = 1;
    private final Map<Integer, Advancement> slotToAdvancement = new HashMap<>();

    private AchievementGui(AchievementCheckPlugin plugin, AdvancementReader reader, Mode mode,
                           int page, Advancement detailAdvancement, int listReturnPage) {
        this.plugin = plugin;
        this.reader = reader;
        this.mode = mode;
        this.page = page;
        this.detailAdvancement = detailAdvancement;
        this.listReturnPage = listReturnPage;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ───────────────────────── スロット配置 ─────────────────────────

    private int size() {
        return plugin.getGuiRows() * 9;
    }

    private int contentSize() {
        return size() - 9; // 最下段はナビ行
    }

    private int slotBack() {
        return size() - 9;
    }

    private int slotPrev() {
        return size() - 6;
    }

    private int slotInfo() {
        return size() - 5;
    }

    private int slotNext() {
        return size() - 4;
    }

    private int slotClose() {
        return size() - 1;
    }

    // ───────────────────────── 公開: 開く ─────────────────────────

    public static void openList(AchievementCheckPlugin plugin, AdvancementReader reader, Player player, int page) {
        AchievementGui gui = new AchievementGui(plugin, reader, Mode.LIST, page, null, page);
        gui.buildList(player);
        player.openInventory(gui.inventory);
    }

    public static void openDetail(AchievementCheckPlugin plugin, AdvancementReader reader, Player player,
                                  Advancement advancement, int page, int listReturnPage) {
        AchievementGui gui = new AchievementGui(plugin, reader, Mode.DETAIL, page, advancement, listReturnPage);
        gui.buildDetail(player);
        player.openInventory(gui.inventory);
    }

    // ───────────────────────── クリック処理 ─────────────────────────

    /** ナビゲーション以外は何もしない（呼び出し側で既にイベントはキャンセル済み）。 */
    public void handleClick(int rawSlot, Player player) {
        if (rawSlot == slotClose()) {
            player.closeInventory();
            return;
        }
        if (rawSlot == slotPrev() && page > 0) {
            reopen(player, page - 1);
            return;
        }
        if (rawSlot == slotNext() && page < pageCount - 1) {
            reopen(player, page + 1);
            return;
        }
        if (mode == Mode.DETAIL) {
            if (rawSlot == slotBack()) {
                openList(plugin, reader, player, listReturnPage);
            }
            return;
        }
        Advancement adv = slotToAdvancement.get(rawSlot);
        if (adv != null) {
            openDetail(plugin, reader, player, adv, 0, page);
        }
    }

    private void reopen(Player player, int newPage) {
        if (mode == Mode.LIST) {
            openList(plugin, reader, player, newPage);
        } else {
            openDetail(plugin, reader, player, detailAdvancement, newPage, listReturnPage);
        }
    }

    // ───────────────────────── 構築: 一覧 ─────────────────────────

    private void buildList(Player player) {
        List<Advancement> all = reader.collectionAdvancements();
        // 未達成を前に（キー順は collectionAdvancements 側で確定済みなので安定ソート）。
        all.sort((a, b) -> Boolean.compare(reader.progressOf(player, a).done(),
                reader.progressOf(player, b).done()));

        int perPage = contentSize();
        pageCount = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        page = clampPage(page);

        this.inventory = Bukkit.createInventory(this, size(),
                noItalic(Component.text("実績チェック  (" + (page + 1) + "/" + pageCount + ")", NamedTextColor.DARK_AQUA)));

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < all.size(); i++) {
            Advancement adv = all.get(start + i);
            inventory.setItem(i, listIcon(player, adv));
            slotToAdvancement.put(i, adv);
        }
        placeNav(false);
    }

    private ItemStack listIcon(Player player, Advancement adv) {
        AdvancementReader.Progress prog = reader.progressOf(player, adv);
        ItemStack icon = iconOf(adv);
        icon.editMeta(meta -> {
            meta.displayName(noItalic(adv.getDisplay().title()));
            List<Component> lore = new ArrayList<>();
            NamedTextColor head = prog.done() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            lore.add(noItalic(Component.text("達成 " + prog.awarded().size() + " / " + prog.total(), head)));
            if (prog.done()) {
                lore.add(noItalic(Component.text("✔ コンプリート！", NamedTextColor.GREEN)));
            } else {
                lore.add(Component.empty());
                lore.add(noItalic(Component.text("未達成:", NamedTextColor.RED)));
                List<String> remaining = prog.remaining();
                int limit = plugin.getHoverMissingLimit();
                int shown = Math.min(remaining.size(), limit);
                for (int i = 0; i < shown; i++) {
                    lore.add(noItalic(Component.text("  ✘ ", NamedTextColor.RED)
                            .append(reader.criterionName(adv, remaining.get(i)).color(NamedTextColor.RED))));
                }
                int rest = remaining.size() - shown;
                if (rest > 0) {
                    lore.add(noItalic(Component.text("  … ほか " + rest + " 件", NamedTextColor.GRAY)));
                }
                lore.add(Component.empty());
                lore.add(noItalic(Component.text("クリックで全件を表示", NamedTextColor.AQUA)));
            }
            meta.lore(lore);
            if (prog.done()) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return icon;
    }

    private ItemStack iconOf(Advancement adv) {
        try {
            ItemStack icon = adv.getDisplay().icon();
            if (icon != null && !icon.getType().isAir()) {
                return icon.clone();
            }
        } catch (RuntimeException ignored) {
            // アイコン取得に失敗しても既定アイコンで続行する。
        }
        return new ItemStack(Material.BOOK);
    }

    // ───────────────────────── 構築: 詳細 ─────────────────────────

    private void buildDetail(Player player) {
        AdvancementReader.Progress prog = reader.progressOf(player, detailAdvancement);
        // 未達成を先頭に並べ、設定で許可されていれば達成済みを後ろに続ける。
        List<String> entries = new ArrayList<>(prog.remaining());
        if (plugin.isShowCompleted()) {
            entries.addAll(prog.awarded());
        }
        int missingCount = prog.remaining().size();

        int perPage = contentSize();
        pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) perPage));
        page = clampPage(page);

        this.inventory = Bukkit.createInventory(this, size(),
                noItalic(Component.empty().color(NamedTextColor.DARK_AQUA)
                        .append(detailAdvancement.getDisplay().title())
                        .append(Component.text("  " + prog.awarded().size() + "/" + prog.total(), NamedTextColor.GRAY))));

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < entries.size(); i++) {
            int idx = start + i;
            boolean awarded = idx >= missingCount; // entries は [未達成…, 達成済み…] の順
            inventory.setItem(i, criterionIcon(entries.get(idx), awarded));
        }
        placeNav(true);
    }

    private ItemStack criterionIcon(String criterion, boolean awarded) {
        Material matched = Material.matchMaterial(criterion);
        Material iconMat = (matched != null && matched.isItem())
                ? matched
                : (awarded ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemStack item = new ItemStack(iconMat);
        item.editMeta(meta -> {
            Component name = reader.criterionName(detailAdvancement, criterion);
            if (awarded) {
                meta.displayName(noItalic(Component.text("✔ ", NamedTextColor.GREEN)
                        .append(name.color(NamedTextColor.GREEN))));
                meta.setEnchantmentGlintOverride(true);
            } else {
                meta.displayName(noItalic(Component.text("✘ ", NamedTextColor.RED)
                        .append(name.color(NamedTextColor.RED))));
            }
        });
        return item;
    }

    // ───────────────────────── ナビ行 ─────────────────────────

    private void placeNav(boolean detail) {
        if (detail) {
            inventory.setItem(slotBack(), navItem(Material.OAK_DOOR, "一覧へ戻る", NamedTextColor.YELLOW));
        }
        if (page > 0) {
            inventory.setItem(slotPrev(), navItem(Material.ARROW, "← 前のページ", NamedTextColor.WHITE));
        }
        if (page < pageCount - 1) {
            inventory.setItem(slotNext(), navItem(Material.ARROW, "次のページ →", NamedTextColor.WHITE));
        }
        inventory.setItem(slotInfo(), navItem(Material.PAPER, "ページ " + (page + 1) + " / " + pageCount, NamedTextColor.GRAY));
        inventory.setItem(slotClose(), navItem(Material.BARRIER, "閉じる", NamedTextColor.RED));
    }

    private ItemStack navItem(Material material, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(noItalic(Component.text(label, color))));
        return item;
    }

    // ───────────────────────── 補助 ─────────────────────────

    private int clampPage(int requested) {
        if (requested < 0) {
            return 0;
        }
        return Math.min(requested, pageCount - 1);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
