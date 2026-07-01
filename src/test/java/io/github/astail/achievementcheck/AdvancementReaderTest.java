package io.github.astail.achievementcheck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdvancementReader} の Bukkit 非依存な純粋関数の単体テスト。
 * サーバーなしで検証できる、条件名の整形と AND/OR 判定を対象にする。
 */
class AdvancementReaderTest {

    // ───────────── humanize ─────────────

    @Test
    void humanizeStripsNamespaceAndTitleCases() {
        assertEquals("Deep Dark", AdvancementReader.humanize("minecraft:deep_dark"));
        assertEquals("Plains", AdvancementReader.humanize("minecraft:plains"));
        assertEquals("Enchanted Golden Apple", AdvancementReader.humanize("minecraft:enchanted_golden_apple"));
    }

    @Test
    void humanizeWorksWithoutNamespace() {
        assertEquals("Bamboo Jungle", AdvancementReader.humanize("bamboo_jungle"));
    }

    @Test
    void humanizeHandlesEmptyAndEdgeInput() {
        assertEquals("", AdvancementReader.humanize(""));
        assertEquals("minecraft:", AdvancementReader.humanize("minecraft:")); // 本体が空ならキーをそのまま返す
    }

    // ───────────── stripNamespace ─────────────

    @Test
    void stripNamespaceRemovesPrefix() {
        assertEquals("plains", AdvancementReader.stripNamespace("minecraft:plains"));
        assertEquals("creeper", AdvancementReader.stripNamespace("creeper"));
    }

    // ───────────── isAndCollection ─────────────

    @Test
    void andCollectionRequiresMultipleAndAllRequired() {
        assertTrue(AdvancementReader.isAndCollection(56, 56));  // Adventuring Time（全バイオーム）
        assertTrue(AdvancementReader.isAndCollection(41, 41));  // Monsters Hunted（全モブ）
        assertTrue(AdvancementReader.isAndCollection(2, 2));    // 最小のコレクション
    }

    @Test
    void orAdvancementsAreExcluded() {
        assertFalse(AdvancementReader.isAndCollection(41, 1));  // Monster Hunter（どれか 1 体で完了）
        assertFalse(AdvancementReader.isAndCollection(5, 1));   // どれか 1 条件で完了する OR 実績
    }

    @Test
    void singleCriterionIsNotCollection() {
        assertFalse(AdvancementReader.isAndCollection(1, 1));
        assertFalse(AdvancementReader.isAndCollection(0, 0));
    }

    // ───────────── trimPatternTranslationKey ─────────────

    @Test
    void trimPatternKeyExtractedFromCompoundCriterion() {
        // 「Smithing with Style」の条件名 → trim_pattern.* に変換される。
        assertEquals("trim_pattern.minecraft.rib",
                AdvancementReader.trimPatternTranslationKey(
                        "armor_trimmed_minecraft:rib_armor_trim_smithing_template_smithing_trim"));
        assertEquals("trim_pattern.minecraft.wayfinder",
                AdvancementReader.trimPatternTranslationKey(
                        "armor_trimmed_minecraft:wayfinder_armor_trim_smithing_template_smithing_trim"));
    }

    @Test
    void trimPatternKeyIsNullForUnrelatedCriteria() {
        assertNull(AdvancementReader.trimPatternTranslationKey("minecraft:plains"));
        assertNull(AdvancementReader.trimPatternTranslationKey("minecraft:creeper"));
        assertNull(AdvancementReader.trimPatternTranslationKey("armor_trimmed_minecraft:smithing_trim"));
    }

    // ───────────── VariantFamily（内蔵日本語名） ─────────────

    @Test
    void variantFamilyReturnsBuiltInJapaneseNames() {
        // 日本語 Minecraft Wiki 準拠の名称。
        assertEquals("三毛", AdvancementReader.VariantFamily.CAT.japaneseName("calico"));
        assertEquals("タキシード", AdvancementReader.VariantFamily.CAT.japaneseName("black"));
        assertEquals("黒", AdvancementReader.VariantFamily.CAT.japaneseName("all_black"));
        assertEquals("黒色", AdvancementReader.VariantFamily.WOLF.japaneseName("black"));
        assertEquals("冷帯種", AdvancementReader.VariantFamily.FROG.japaneseName("cold"));
    }

    @Test
    void variantFamilyReturnsNullForUnknownOrNone() {
        assertNull(AdvancementReader.VariantFamily.CAT.japaneseName("unknown_variant"));
        assertNull(AdvancementReader.VariantFamily.NONE.japaneseName("calico"));
    }
}
