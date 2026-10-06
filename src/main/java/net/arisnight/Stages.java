package net.arisnight;

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

    public static String label(int stage) {
        return switch (stage) {
            case 1 -> "раз в 3 сек";
            case 2 -> "раз в 2 сек";
            case 3 -> "раз в 1.5 сек";
            case 4 -> "раз в 1 сек";
            case 5 -> "x1.5";
            default -> "x" + (stage - 4);
        };
    }
}