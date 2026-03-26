package com.mechanism.aichatassistant;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModKeywordRegistry {

    public record ModCategory(String name, List<String> triggerWords, List<String> modIdPatterns) {}

    private static final List<ModCategory> CATEGORIES = List.of(
        new ModCategory("LAND_PROTECTION",
            List.of("приват", "привате", "привата", "claim", "protect", "protection", "grief", "land", "территория", "защита", "участок"),
            List.of("griefprevention", "claim", "protect", "land", "worldguard", "residence", "towny", "factions")
        ),
        new ModCategory("MAGIC",
            List.of("магия", "маги", "mana", "spell", "заклинание", "зелье", "potion", "botania", "thaumcraft", "ars"),
            List.of("botania", "thaumcraft", "ars", "magic", "blood", "witchery", "hexerei")
        ),
        new ModCategory("TECH",
            List.of("техника", "механизм", "энергия", "energy", "rf", "forge", "mekanism", "машина", "генератор", "труба", "pipe", "радиация", "дозиметр", "radiation", "химия", "газ", "реактор", "reactor", "chemical"),
            List.of("mekanism", "industrialcraft", "thermal", "applied", "ae2", "create", "buildcraft", "enderio")
        ),
        new ModCategory("STORAGE",
            List.of("хранилище", "склад", "сундук", "chest", "storage", "items", "предметы", "me ", "ae"),
            List.of("ae2", "appliedenergistics", "storage", "drawers", "chest", "warehouse")
        ),
        new ModCategory("TELEPORT",
            List.of("телепорт", "teleport", "home", "дом", "варп", "warp", "spawn", "спавн", "точка", "tpa"),
            List.of("essentials", "homes", "warps", "teleport", "tpa", "sethome")
        ),
        new ModCategory("ECONOMY",
            List.of("деньги", "магазин", "shop", "economy", "экономика", "купить", "продать", "buy", "sell", "цена", "монеты"),
            List.of("economy", "shop", "market", "trade", "essentials", "cmishop")
        ),
        new ModCategory("MOBS",
            List.of("моб", "мобы", "spawn", "спавн", "существо", "creature", "monster", "животное", "animal"),
            List.of("mob", "spawn", "creature", "animal", "lycanites", "iceandfire")
        ),
        new ModCategory("QUESTS",
            List.of("квест", "задание", "quest", "mission", "задача", "ftbquests", "бтб"),
            List.of("ftbquests", "quest", "bountiful", "hqm")
        )
    );

    public static Set<ModCategory> findCategories(String question) {
        String normalized = question.toLowerCase().replaceAll("[.,!?;:]", " ");
        Set<ModCategory> result = new HashSet<>();
        for (ModCategory category : CATEGORIES) {
            for (String word : category.triggerWords()) {
                if (normalized.contains(word)) {
                    result.add(category);
                    break;
                }
            }
        }
        return result;
    }

    public static boolean isModRelevant(String modId, String displayName, Set<ModCategory> categories) {
        String id = modId.toLowerCase();
        String name = displayName != null ? displayName.toLowerCase() : "";
        for (ModCategory category : categories) {
            for (String pattern : category.modIdPatterns()) {
                if (id.contains(pattern) || name.contains(pattern)) return true;
            }
        }
        return false;
    }
}
