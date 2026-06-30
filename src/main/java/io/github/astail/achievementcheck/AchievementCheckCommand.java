package io.github.astail.achievementcheck;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /achievementcheck}（別名 {@code /ac}）の実処理とタブ補完。
 *
 * <ul>
 *   <li>{@code /ac} … 実績確認 GUI を開く（プレイヤー専用）。</li>
 *   <li>{@code /ac <実績名>} … 指定実績の不足条件・達成条件をチャットに表示（プレイヤー専用）。</li>
 *   <li>{@code /ac reload} … 設定を再読み込み（権限 {@code achievementcheck.manage}、コンソール可）。</li>
 * </ul>
 */
public final class AchievementCheckCommand implements CommandExecutor, TabCompleter {

    private final AchievementCheckPlugin plugin;
    private final AdvancementReader reader;

    public AchievementCheckCommand(AchievementCheckPlugin plugin, AdvancementReader reader) {
        this.plugin = plugin;
        this.reader = reader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("achievementcheck.manage")) {
                sender.sendMessage(error("この操作（reload）はサーバー管理者のみ実行できます。"));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(ok("設定を再読み込みしました（状態: " + (plugin.isActive() ? "ON" : "OFF") + "）。"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("このコマンドはプレイヤーから実行してください。"));
            return true;
        }
        if (!plugin.isActive()) {
            player.sendMessage(error("AchievementCheck は現在無効です（config の enabled が false）。"));
            return true;
        }

        if (args.length == 0) {
            AchievementGui.openList(plugin, reader, player, 0);
            return true;
        }

        String query = String.join(" ", args).toLowerCase(Locale.ROOT);
        Advancement match = findAdvancement(query);
        if (match == null) {
            player.sendMessage(error("該当する実績が見つかりません: " + query));
            player.sendMessage(info("/ac でメニューを開くか、タブ補完で実績名を選んでください。"));
            return true;
        }
        printDetail(player, match);
        return true;
    }

    /** クエリに一致するコレクション系実績を探す（完全一致を優先、無ければ部分一致）。 */
    private Advancement findAdvancement(String query) {
        Advancement partial = null;
        for (Advancement adv : reader.collectionAdvancements()) {
            String path = adv.getKey().getKey();             // 例: adventure/adventuring_time
            String shortKey = path.substring(path.indexOf('/') + 1); // 例: adventuring_time
            if (shortKey.equalsIgnoreCase(query) || path.equalsIgnoreCase(query)) {
                return adv;
            }
            if (partial == null && (shortKey.contains(query) || path.contains(query)
                    || AdvancementReader.humanize(shortKey).toLowerCase(Locale.ROOT).contains(query))) {
                partial = adv;
            }
        }
        return partial;
    }

    private void printDetail(Player player, Advancement adv) {
        AdvancementReader.Progress prog = reader.progressOf(player, adv);
        player.sendMessage(noItalic(Component.text("=== ", NamedTextColor.DARK_AQUA)
                .append(adv.getDisplay().title())
                .append(Component.text(" (" + prog.awarded().size() + "/" + prog.total() + ") ===", NamedTextColor.DARK_AQUA))));
        if (prog.done()) {
            player.sendMessage(ok("✔ この実績はコンプリート済みです！"));
            return;
        }
        player.sendMessage(Component.text("未達成 (" + prog.remaining().size() + " 件):", NamedTextColor.RED));
        sendCriteria(player, adv, prog.remaining(), NamedTextColor.RED, "✘");
        if (plugin.isShowCompleted() && !prog.awarded().isEmpty()) {
            player.sendMessage(Component.text("達成済み (" + prog.awarded().size() + " 件):", NamedTextColor.GREEN));
            sendCriteria(player, adv, prog.awarded(), NamedTextColor.GREEN, "✔");
        }
    }

    private void sendCriteria(Player player, Advancement adv, List<String> criteria, NamedTextColor color, String mark) {
        int shown = Math.min(criteria.size(), plugin.getMaxList());
        for (int i = 0; i < shown; i++) {
            player.sendMessage(noItalic(Component.text("  " + mark + " ", color)
                    .append(reader.criterionName(adv, criteria.get(i)).color(color))));
        }
        int rest = criteria.size() - shown;
        if (rest > 0) {
            player.sendMessage(info("  … ほか " + rest + " 件"));
        }
    }

    // ───────────────────────── タブ補完 ─────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("achievementcheck.manage")) {
                options.add("reload");
            }
            for (Advancement adv : reader.collectionAdvancements()) {
                String path = adv.getKey().getKey();
                options.add(path.substring(path.indexOf('/') + 1));
            }
            return prefix(options, args[0]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    // ───────────────────────── 着色 ─────────────────────────

    private static Component ok(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component info(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
