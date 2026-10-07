package net.arisnight;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class GazeCloneClient implements ClientModInitializer {
    private static final int SEGMENTS = 20;
    private static final int SEG_W = 8;
    private static final int SEG_GAP = 1;
    private static final int SEG_H = 4;

    private static final int ICON_W = 12;

    private static final String[] EYE = {
            "..XXXXX..",
            ".XWIIIWX.",
            "XWWIPIWWX",
            ".XWIIIWX.",
            "..XXXXX.."
    };

    private static final int COLOR_EMPTY = 0xFF3A3A3A;

    private static final int[] GRADIENT = {0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF3030};
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_PANEL = 0x90000000;

    private static final int BOTTOM_OFFSET = 88;

    private static volatile int advancements = 0;
    private static volatile boolean active = false;
    private static volatile boolean looking = false;

    private static boolean initialized = false;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientPlayNetworking.registerGlobalReceiver(MultiplierPayload.TYPE, (payload, context) -> {
            advancements = payload.advancements();
            looking = payload.looking();
            active = true;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath(GazeClone.MOD_ID, "multiplier_hud"),
                GazeCloneClient::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int adv = advancements;
        int stage = Stages.stageFor(adv);
        Component label = Stages.label(stage);

        Component title = Component.translatable("gazeclone.hud.multiplier");
        Component sep = Component.literal("  \u2022 ");
        Component ach = Component.translatable("gazeclone.hud.advancements", adv);

        int textW = ICON_W + font.width(title) + font.width(label) + font.width(sep) + font.width(ach);
        int barW = SEGMENTS * SEG_W + (SEGMENTS - 1) * SEG_GAP;
        int contentW = Math.max(textW, barW);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int top = screenH - BOTTOM_OFFSET;
        int left = (screenW - contentW) / 2;

        graphics.fill(left - 5, top - 4, left + contentW + 5, top + 9 + 4 + SEG_H + 4, COLOR_PANEL);

        int accent = segmentColor(Math.min(stage, SEGMENTS) - 1);

        int x = left + (contentW - textW) / 2;
        x = drawIcon(graphics, font, x, top, accent, looking);
        x = drawPart(graphics, font, title, x, top, COLOR_WHITE);
        x = drawPart(graphics, font, label, x, top, accent);
        x = drawPart(graphics, font, sep, x, top, COLOR_GRAY);
        drawPart(graphics, font, ach, x, top, COLOR_GRAY);

        int barY = top + 9 + 4;
        int barX = left + (contentW - barW) / 2;
        int lit = Math.min(stage, SEGMENTS);

        for (int i = 0; i < SEGMENTS; i++) {
            int sx = barX + i * (SEG_W + SEG_GAP);
            graphics.fill(sx, barY, sx + SEG_W, barY + SEG_H, i < lit ? segmentColor(i) : COLOR_EMPTY);
        }
    }

    private static int segmentColor(int index) {
        float t = Math.max(0, Math.min(index, SEGMENTS - 1)) / (float) (SEGMENTS - 1);
        float scaled = t * (GRADIENT.length - 1);
        int i = Math.min((int) scaled, GRADIENT.length - 2);
        return lerpColor(GRADIENT[i], GRADIENT[i + 1], scaled - i);
    }

    private static int lerpColor(int a, int b, float f) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int drawIcon(GuiGraphicsExtractor graphics, Font font, int x, int y, int accent, boolean eye) {
        if (!eye) {
            graphics.text(font, "\u26A1", x, y, accent, true);
            return x + ICON_W;
        }
        int ey = y + 2;
        for (int row = 0; row < EYE.length; row++) {
            String line = EYE[row];
            for (int col = 0; col < line.length(); col++) {
                int color;
                switch (line.charAt(col)) {
                    case 'X', 'I' -> color = accent;
                    case 'W' -> color = 0xFFFFFFFF;
                    case 'P' -> color = 0xFF000000;
                    default -> { continue; }
                }
                graphics.fill(x + col, ey + row, x + col + 1, ey + row + 1, color);
            }
        }
        return x + ICON_W;
    }

    private static int drawPart(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, true);
        return x + font.width(text);
    }
}