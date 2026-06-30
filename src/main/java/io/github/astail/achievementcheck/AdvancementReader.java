package io.github.astail.achievementcheck;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.advancement.AdvancementRequirements;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 実績（advancement）の読み取りと条件名の解決を担う中核。
 *
 * <p>サーバーに登録された実績を列挙し、「コレクション系」（複数条件をすべて満たす AND 実績）を抽出する。
 * 各プレイヤーについて達成済み／未達成の条件を取得し、条件キー（例 {@code minecraft:plains}）を
 * クライアント言語でローカライズされる translatable Component に変換する。</p>
 */
public final class AdvancementReader {

    private final AchievementCheckPlugin plugin;

    /** 実績ごとのバリアント種別（猫/狼/蛙）のキャッシュ。実行中は実績が不変なので一度判定すれば足りる。 */
    private final Map<NamespacedKey, VariantFamily> variantFamilyCache = new ConcurrentHashMap<>();

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
     * 条件キー（例 {@code minecraft:plains}）を、実績の文脈に応じて表示用 Component にする。
     *
     * <p>猫/狼/蛙のバリアント実績はバニラに翻訳文字列が（どの言語にも）存在しないため、本プラグインが
     * 内蔵する日本語名を使う（{@link VariantFamily}）。それ以外は translatable で送り、クライアント言語で
     * 表示する。translatable で解決できないときは整形したキー名にフォールバックする。</p>
     */
    public Component criterionName(Advancement adv, String criterion) {
        VariantFamily family = variantFamilyOf(adv);
        if (family != VariantFamily.NONE) {
            String name = family.japaneseName(stripNamespace(criterion));
            if (name != null) {
                return Component.text(name);
            }
        }
        return Component.translatable(translationKeyFor(criterion), humanize(stripNamespace(criterion)));
    }

    /** 実績のバリアント種別を判定（キャッシュ付き）。 */
    private VariantFamily variantFamilyOf(Advancement adv) {
        return variantFamilyCache.computeIfAbsent(adv.getKey(), k -> computeVariantFamily(adv));
    }

    /**
     * 実績の全条件が単一のバリアントレジストリ（猫/狼/蛙）に属するなら、その種別を返す。
     * 全条件をまとめて見ることで、猫・狼に共通する {@code black} のような id も取り違えない。
     */
    private static VariantFamily computeVariantFamily(Advancement adv) {
        Collection<String> criteria = adv.getCriteria();
        if (criteria.isEmpty()) {
            return VariantFamily.NONE;
        }
        if (allCriteriaIn(criteria, RegistryKey.CAT_VARIANT)) {
            return VariantFamily.CAT;
        }
        if (allCriteriaIn(criteria, RegistryKey.WOLF_VARIANT)) {
            return VariantFamily.WOLF;
        }
        if (allCriteriaIn(criteria, RegistryKey.FROG_VARIANT)) {
            return VariantFamily.FROG;
        }
        return VariantFamily.NONE;
    }

    /** 全条件キーが指定レジストリに存在するか。公式 API（{@link RegistryAccess}）のみで判定する。 */
    private static <T extends Keyed> boolean allCriteriaIn(Collection<String> criteria, RegistryKey<T> regKey) {
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(regKey);
        for (String criterion : criteria) {
            NamespacedKey key = NamespacedKey.fromString(criterion);
            if (key == null || registry.get(key) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 条件キーから、クライアントが解釈する translation key を推定する。
     * 防具の鍛冶模様（複合条件名）→ trim_pattern.*、Mob → entity.*、アイテム/ブロック → block./item.*、
     * それ以外（バイオーム等）→ biome.* を試みる。
     */
    static String translationKeyFor(String criterion) {
        String trimKey = trimPatternTranslationKey(criterion);
        if (trimKey != null) {
            return trimKey;
        }
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

    /**
     * 「Smithing with Style」の条件名から鍛冶模様の translation key を取り出す。Bukkit 非依存の純粋関数。
     *
     * <p>条件名は {@code armor_trimmed_<ns>:<pattern>_armor_trim_smithing_template_smithing_trim} 形式で、
     * これを {@code trim_pattern.<ns>.<pattern>}（例 {@code trim_pattern.minecraft.rib}）に変換する。
     * バニラに翻訳キーがあるためクライアント言語で正しく表示される。形式が一致しなければ {@code null}。</p>
     */
    static String trimPatternTranslationKey(String criterion) {
        final String prefix = "armor_trimmed_";
        final String suffix = "_armor_trim_smithing_template_smithing_trim";
        if (!criterion.startsWith(prefix) || !criterion.endsWith(suffix)) {
            return null;
        }
        String mid = criterion.substring(prefix.length(), criterion.length() - suffix.length());
        int colon = mid.indexOf(':');
        String namespace = colon >= 0 ? mid.substring(0, colon) : "minecraft";
        String pattern = colon >= 0 ? mid.substring(colon + 1) : mid;
        if (namespace.isEmpty() || pattern.isEmpty()) {
            return null;
        }
        return "trim_pattern." + namespace + "." + pattern;
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

    /**
     * 猫・狼・蛙のバリアント実績と、その内蔵日本語名。
     *
     * <p>これらのバリアント名はバニラの言語ファイルに翻訳文字列が存在しない（どの言語でも
     * {@code entity.minecraft.cat} = "Cat" のような親エンティティ名しか無い）。そのため translatable では
     * 日本語化できず、本プラグインが日本語名を持つ。<b>名称は日本語 Minecraft Wiki に準拠し、クライアント言語に
     * 関わらず日本語で表示される</b>（GUI のラベルが既に日本語ハードコードなのと同じ方針）。未知の id は
     * {@code null} を返し、呼び出し側で整形キー名にフォールバックする。</p>
     */
    enum VariantFamily {
        NONE(Map.of()),
        // 「かわいいだけじゃない」: 猫の品種（日本語 Wiki 準拠）。
        // black=タキシード柄(cat_black)・all_black=真っ黒(cat_all_black) はテクスチャ asset で確認済み。
        CAT(Map.ofEntries(
                Map.entry("tabby", "トラ"),
                Map.entry("black", "タキシード"),
                Map.entry("red", "レッド"),
                Map.entry("siamese", "シャム"),
                Map.entry("british_shorthair", "ブリティッシュショートヘア"),
                Map.entry("calico", "三毛"),
                Map.entry("persian", "ペルシャ"),
                Map.entry("ragdoll", "ラグドール"),
                Map.entry("white", "白"),
                Map.entry("jellie", "ジェリー"),
                Map.entry("all_black", "黒"))),
        // 「群れの一員」: オオカミの毛色（日本語 Wiki 準拠）。
        WOLF(Map.ofEntries(
                Map.entry("pale", "白色"),
                Map.entry("ashen", "灰色"),
                Map.entry("black", "黒色"),
                Map.entry("chestnut", "栗色"),
                Map.entry("rusty", "赤茶"),
                Map.entry("snowy", "雪"),
                Map.entry("spotted", "まだら"),
                Map.entry("striped", "しま"),
                Map.entry("woods", "森"))),
        // 「When the Squad Hops into Town」: カエルの種類（日本語 Wiki 準拠）。
        FROG(Map.ofEntries(
                Map.entry("temperate", "温帯種"),
                Map.entry("warm", "熱帯種"),
                Map.entry("cold", "冷帯種")));

        private final Map<String, String> names;

        VariantFamily(Map<String, String> names) {
            this.names = names;
        }

        /** バリアント id（名前空間なし）の日本語名。無ければ {@code null}。 */
        String japaneseName(String id) {
            return names.get(id);
        }
    }
}
