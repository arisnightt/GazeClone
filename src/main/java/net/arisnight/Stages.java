package net.arisnight;

import net.minecraft.network.chat.Component;

public final class Stages {
    private Stages() {}

    public static int stageFor(int advancements) {
        return advancements + 1;
    }

    public static int cycleTicks(int stage) {
        return switch (stage) {
            case 1 -> 60;
            case 2 -> 40;
            case 3 -> 30;
            default -> 20;
        };
    }

    public static double multiplier(int stage) {
        if (stage <= 4) {
            return 2.0;
        }
        if (stage == 5) {
            return 1.5;
        }
        return stage - 4;
    }

    public static double clonesPerCycle(int stage) {
        return multiplier(stage) - 1.0;
    }

    public static Component label(int stage) {
        return switch (stage) {
            case 1 -> Component.translatable("gazeclone.stage.interval", "3");
            case 2 -> Component.translatable("gazeclone.stage.interval", "2");
            case 3 -> Component.translatable("gazeclone.stage.interval", "1.5");
            case 4 -> Component.translatable("gazeclone.stage.interval", "1");
            case 5 -> Component.translatable("gazeclone.stage.multiplier", "1.5");
            default -> Component.translatable("gazeclone.stage.multiplier", String.valueOf(stage - 4));
        };
    }
}