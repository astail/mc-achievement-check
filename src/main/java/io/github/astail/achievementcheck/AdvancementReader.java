package io.github.astail.achievementcheck;

import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.advancement.AdvancementRequirements;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 実績（advancement）の読み取りと条件名の解決を担う中核。
 *
 * <p>サーバーに登録された実績を列挙し、「コレクション系」（複数条件をすべて満たす AND 実績）を抽出する。
 * 各プレイヤーについて達成済み／未達成の条件を取得し、条件キー（例 {@code minecraft:plains}）を
 * クライアント言語でローカライズされる translatable Component に変換する。</p>
 */
public final class AdvancementReader {

    private final AchievementCheckPlugin plugin;

    public AdvancementReader(AchievementCheckPlugin plugin) {
        this.plugin = plugin;
    }

    // ───────────────────────── 実績の列挙 ─────────────────────────

    /**
     * 「コレクション系」のバニラ実績（複数条件 AND・表示あり・レシピ解禁以外）をキー順で返す。
     * 不足条件を列挙する意味があるものだけを対象にする。
     */
    public List<Advancement> collectionAdvancements() {
        List<Advancement> out = new ArrayList<>();
        Iterator<Advancement> it = plugin.getServer().advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            if (isCollectionAdvancement(adv)) {
                out.add(adv);
            }
        }
        out.sort((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));
        return out;
    }

    /**
     * コレクション系実績かどうかを判定する。
     * 表示を持ち（レシピ解禁などの非表示実績を除外）、複数条件がすべて必須（AND）のものを対象にする。
     * OR 実績（どれか 1 つ満たせば完了）は「不足条件」の概念が合わないため除外する。
     */
    public boolean isCollectionAdvancement(Advancement adv) {
        AdvancementDisplay display = adv.getDisplay();
        if (display == null) {
            return false; // レシピ解禁などの非表示実績
        }
        if (adv.getKey().getKey().startsWith("recipes/")) {
            return false;
        }
        int criteria = adv.getCriteria().size();
        int groups = requirementGroupCount(adv, criteria);
        return isAndCollection(criteria, groups);
    }

    /** 要件グループ数（取得できなければ条件数を採用 = 全 AND とみなす）。 */
    private static int requirementGroupCount(Advancement adv, int criteriaCount) {
        AdvancementRequirements reqs = adv.getRequirements();
        if (reqs == null || reqs.getRequirements() == null) {
            return criteriaCount;
        }
        return reqs.getRequirements().size();
    }

    /**
     * 全条件 AND の複数条件実績か（不足条件の列挙に意味があるもの）。
     * AND 実績は「条件ごとに 1 要件グループ」になるため、要件グループ数 == 条件数 で判別できる。
     * OR 実績は全条件が 1 グループにまとまるため除外される。Bukkit 非依存の純粋関数。
     */
    public static boolean isAndCollection(int criteriaCount, int requirementGroupCount) {
        return criteriaCount > 1 && criteriaCount == requirementGroupCount;
    }

    // ───────────────────────── 進捗 ─────────────────────────

    /** 指定プレイヤーの、指定実績に対する進捗（未達成を前にソート済み）。 */
    public Progress progressOf(Player player, Advancement adv) {
        AdvancementProgress p = player.getAdvancementProgress(adv);
        List<String> awarded = new ArrayList<>(p.getAwardedCriteria());
        List<String> remaining = new ArrayList<>(p.getRemainingCriteria());
        awarded.sort(String::compareTo);
        remaining.sort(String::compareTo);
        return new Progress(adv, awarded, remaining, p.isDone());
    }

    /** 1 実績ぶんの進捗スナップショット。 */
    public record Progress(Advancement advancement, List<String> awarded, List<String> remaining, boolean done) {
        public int total() {
            return awarded.size() + remaining.size();
        }
    }

    // ───────────────────────── 条件名の解決 ─────────────────────────

    /**
     * 条件キー（例 {@code minecraft:plains}）を表示用 Component にする。
     * translatable なのでクライアント言語で表示され、翻訳できないときは整形したキーをフォールバック表示する。
     */
    public Component criterionName(String criterion) {
        return Component.translatable(translationKeyFor(criterion), humanize(stripNamespace(criterion)));
    }

    /**
     * 条件キーから、クライアントが解釈する translation key を推定する。
     * Mob → entity.*、アイテム/ブロック → block./item.*、それ以外（バイオーム等）→ biome.* を試みる。
     */
    static String translationKeyFor(String criterion) {
        NamespacedKey key = NamespacedKey.fromString(criterion);
        String namespace = key != null ? key.getNamespace() : "minecraft";
        String path = key != null ? key.getKey() : stripNamespace(criterion);
        if (key != null) {
            EntityType entity = Registry.ENTITY_TYPE.get(key);
            if (entity != null) {
                return entity.translationKey();
            }
        }
        Material material = Material.matchMaterial(criterion);
        if (material != null) {
            return material.translationKey();
        }
        return "biome." + namespace + "." + path;
    }

    /** {@code "minecraft:deep_dark"} → {@code "Deep Dark"}。Bukkit 非依存の純粋関数。 */
    public static String humanize(String key) {
        String body = stripNamespace(key).replace('_', ' ').trim();
        if (body.isEmpty()) {
            return key;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : body.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    /** {@code "minecraft:plains"} → {@code "plains"}。Bukkit 非依存の純粋関数。 */
    public static String stripNamespace(String key) {
        int idx = key.indexOf(':');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }
}
