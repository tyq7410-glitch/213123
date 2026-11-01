package dev.aurora.core;

public enum Category {
    COMBAT("PvP"), MOVEMENT("Движение"), VISUAL("Визуалы"), PVE("PvE"), MISC("Утилиты"), COSMETIC("Косметика"),
    CLIENT("Интерфейс"), ASSISTANT("Помощник");
    public final String label;
    Category(String label) { this.label = label; }
}
