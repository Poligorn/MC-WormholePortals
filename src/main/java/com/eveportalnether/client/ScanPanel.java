package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.network.PortalScanPayload;
import com.eveportalnether.world.WormholeStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * HUD panel with the detector readout: a live miniature of the portal on the left,
 * decrypting fields on the right. Does not take focus and fades out on its own.
 */
public final class ScanPanel {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "scan_panel");

    private static final int WIDTH = 250;
    private static final int HEIGHT = 102;
    private static final int PREVIEW_WIDTH = 68;
    private static final int HOLD_TICKS = 200;
    private static final int LOST_HOLD_TICKS = 40;
    private static final int FADE_TICKS = 20;
    private static final int SLIDE_TICKS = 6;

    private static final int COLOR_BG = 0x0A0D12;
    private static final int COLOR_EDGE = 0x2A3440;
    private static final int COLOR_LABEL = 0x7A8DA0;
    private static final int COLOR_VALUE = 0xE6EEF5;
    private static final int COLOR_TITLE = 0xA9BCCF;
    private static final int COLOR_LOST = 0xFF4B4B;
    private static final int COLOR_DONE = 0x5CFF7A;
    private static final int COLOR_PENDING = 0xFFD24B;
    private static final String GLYPHS = "#@%&$?*!~^01";
    private static final int LINES = 6;

    private static PortalScanPayload scan;
    private static long startTick;
    private static long lostTick = -1;

    private ScanPanel() {
    }

    public static void show(PortalScanPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        scan = payload;
        startTick = minecraft.level.getGameTime();
        lostTick = -1;
    }

    public static void lost() {
        Minecraft minecraft = Minecraft.getInstance();
        if (scan != null && lostTick < 0 && minecraft.level != null) {
            lostTick = minecraft.level.getGameTime();
        }
    }

    public static void clear() {
        scan = null;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        PortalScanPayload data = scan;
        if (data == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        float now = minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        float elapsed = now - startTick;
        int decryptTicks = Math.max(1, data.decryptTicks());
        boolean isLost = lostTick >= 0;
        float progress = isLost
                ? Math.min(1.0F, Math.max(0.0F, (lostTick - startTick) / (float) decryptTicks))
                : Math.min(1.0F, Math.max(0.0F, elapsed / decryptTicks));

        float alpha;
        if (isLost) {
            float since = now - lostTick;
            alpha = 1.0F - Math.max(0.0F, since - LOST_HOLD_TICKS) / FADE_TICKS;
        } else {
            alpha = 1.0F - Math.max(0.0F, elapsed - decryptTicks - HOLD_TICKS) / FADE_TICKS;
        }
        if (alpha <= 0.03F) {
            scan = null;
            return;
        }
        alpha = Math.min(1.0F, alpha);

        float slideIn = Math.min(1.0F, elapsed / SLIDE_TICKS);
        int slide = (int) ((1.0F - slideIn) * (1.0F - slideIn) * 40);
        int x = g.guiWidth() - WIDTH - 10 + slide;
        int y = g.guiHeight() / 2 - HEIGHT / 2 - 16;
        int accent = data.color().rgb();

        drawFrame(g, x, y, accent, alpha);
        drawHeader(g, minecraft.font, x, y, progress, isLost, alpha);
        drawPreview(g, data, x + 6, y + 21, PREVIEW_WIDTH, HEIGHT - 27, now, elapsed, progress, isLost, alpha);
        drawFields(g, minecraft.font, data, x + PREVIEW_WIDTH + 12, y + 22, now, elapsed, progress, isLost, alpha);
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int accent, float alpha) {
        g.fill(x, y, x + WIDTH, y + HEIGHT, argb(alpha * 0.85F, COLOR_BG));
        g.fill(x, y, x + WIDTH, y + 1, argb(alpha, accent));
        g.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, argb(alpha * 0.4F, accent));
        g.fill(x, y + 1, x + 1, y + HEIGHT - 1, argb(alpha, COLOR_EDGE));
        g.fill(x + WIDTH - 1, y + 1, x + WIDTH, y + HEIGHT - 1, argb(alpha, COLOR_EDGE));
        int bracket = 7;
        g.fill(x - 2, y - 2, x + bracket, y - 1, argb(alpha, accent));
        g.fill(x - 2, y - 2, x - 1, y + bracket, argb(alpha, accent));
        g.fill(x + WIDTH - bracket, y + HEIGHT + 1, x + WIDTH + 2, y + HEIGHT + 2, argb(alpha, accent));
        g.fill(x + WIDTH + 1, y + HEIGHT - bracket, x + WIDTH + 2, y + HEIGHT + 2, argb(alpha, accent));
        g.fill(x + 4, y + 16, x + WIDTH - 4, y + 17, argb(alpha, COLOR_EDGE));
    }

    private static void drawHeader(GuiGraphics g, Font font, int x, int y, float progress, boolean isLost, float alpha) {
        g.drawString(font, Component.translatable("eveportalnether.scan.title").getString(), x + 6, y + 5, argb(alpha, COLOR_TITLE), false);
        String status;
        int color;
        if (isLost) {
            status = Component.translatable("eveportalnether.scan.status.lost").getString();
            color = COLOR_LOST;
        } else if (progress < 1.0F) {
            status = Component.translatable("eveportalnether.scan.status.decrypting", (int) (progress * 100)).getString();
            color = COLOR_PENDING;
        } else {
            status = Component.translatable("eveportalnether.scan.status.done").getString();
            color = COLOR_DONE;
        }
        g.drawString(font, status, x + WIDTH - 6 - font.width(status), y + 5, argb(alpha, color), false);
    }

    private static void drawPreview(GuiGraphics g, PortalScanPayload data, int bx, int by, int bw, int bh,
                                    float now, float elapsed, float progress, boolean isLost, float alpha) {
        g.fill(bx, by, bx + bw, by + bh, argb(alpha, 0x05070A));
        g.fill(bx, by, bx + bw, by + 1, argb(alpha, COLOR_EDGE));
        g.fill(bx, by + bh - 1, bx + bw, by + bh, argb(alpha, COLOR_EDGE));

        int w = Math.max(3, data.width());
        int h = Math.max(4, data.height());
        int cell = Math.max(1, Math.min((bw - 8) / w, (bh - 8) / h));
        int pw = cell * w;
        int ph = cell * h;
        int ox = bx + (bw - pw) / 2;
        int oy = by + (bh - ph) / 2;

        int tint = data.color().tint(data.stage() < 0 ? 3 : data.stage());
        int innerX0 = ox + cell;
        int innerY0 = oy + cell;
        int innerX1 = ox + pw - cell;
        int innerY1 = oy + ph - cell;
        int windowW = Math.max(1, w - 2);
        int windowH = Math.max(1, h - 2);
        int innerWpx = Math.max(1, innerX1 - innerX0);
        int innerHpx = Math.max(1, innerY1 - innerY0);
        int samplesX = Math.min(innerWpx, Math.max(8, windowW * WormholeStyle.vortexSubdiv(windowW)));
        int samplesY = Math.min(innerHpx, Math.max(8, windowH * WormholeStyle.vortexSubdiv(windowH)));
        float halfW = windowW * 0.5F;
        float halfH = windowH * 0.5F;
        for (int sy = 0; sy < samplesY; sy++) {
            int y0 = innerY0 + sy * innerHpx / samplesY;
            int y1 = innerY0 + (sy + 1) * innerHpx / samplesY;
            float dy = (sy + 0.5F) / samplesY * windowH - halfH;
            for (int sx = 0; sx < samplesX; sx++) {
                int x0 = innerX0 + sx * innerWpx / samplesX;
                int x1 = innerX0 + (sx + 1) * innerWpx / samplesX;
                float dx = (sx + 0.5F) / samplesX * windowW - halfW;
                int rgb = WormholeStyle.vortexRgb(tint, dx, dy, now, windowW, windowH);
                g.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1), argb(alpha, rgb));
            }
        }
        int rimPx = Math.max(1, Math.round(WormholeStyle.RIM * Math.max(cell, 8)));
        int haloPx = Math.max(rimPx + 1, Math.round(WormholeStyle.HALO * Math.max(cell, 8)));
        ring(g, innerX0 - haloPx, innerY0 - haloPx, innerX1 + haloPx, innerY1 + haloPx, haloPx,
                argb(alpha * WormholeStyle.HALO_ALPHA / 255.0F, WormholeStyle.whiteRgb()));
        ring(g, innerX0 - rimPx, innerY0 - rimPx, innerX1 + rimPx, innerY1 + rimPx, rimPx,
                argb(alpha, WormholeStyle.whiteRgb()));

        if (isLost) {
            for (int i = 0; i < 5; i++) {
                int gy = by + 2 + (int) (noise(i, (int) (now / 2), 7) * (bh - 4));
                int gx = (int) (noise(i, (int) (now / 2), 11) * bw / 3);
                g.fill(bx + gx, gy, bx + bw - 2, gy + 1 + (i % 2), argb(alpha * 0.8F, COLOR_LOST));
            }
        } else if (progress < 1.0F) {
            int sweep = by + 1 + (int) (((elapsed % 24) / 24.0F) * (bh - 3));
            g.fill(bx + 1, sweep, bx + bw - 1, sweep + 1, argb(alpha * 0.7F, data.color().rgb()));
            g.fill(bx + 1, sweep - 2, bx + bw - 1, sweep, argb(alpha * 0.2F, data.color().rgb()));
        }
    }

    private static void drawFields(GuiGraphics g, Font font, PortalScanPayload data, int ix, int iy,
                                   float now, float elapsed, float progress, boolean isLost, float alpha) {
        String[] labels = {"id", "class", "stage", "stability", "time", "uses"};
        String[] labelText = new String[labels.length];
        int labelWidth = 0;
        for (int i = 0; i < labels.length; i++) {
            labelText[i] = Component.translatable("eveportalnether.scan.label." + labels[i]).getString();
            labelWidth = Math.max(labelWidth, font.width(labelText[i]));
        }
        int valueX = ix + labelWidth + 6;
        int lineHeight = 12;
        int stageColor = switch (data.stage()) {
            case -1 -> 0xD68CFF;
            case 0 -> 0x5CFF7A;
            case 1 -> 0xFFE14B;
            case 2 -> 0xFF7A3B;
            default -> 0xFF3B3B;
        };
        String stageKey = data.stage() < 0 ? "eveportalnether.stage.forming" : "eveportalnether.stage." + data.stage();
        int remainingTicks = Math.max(0, data.ticksRemaining() - (int) elapsed);
        int seconds = remainingTicks / 20;

        String[] values = {
                data.signature(),
                Component.translatable(data.tier().translationKey()).getString(),
                Component.translatable(stageKey).getString(),
                String.format("%.0f%%", data.stability()),
                String.format("%02d:%02d", seconds / 60, seconds % 60),
                String.valueOf(data.usesRemaining())
        };
        int[] colors = {data.color().rgb(), data.tier().textColor(), stageColor, COLOR_VALUE, COLOR_VALUE, COLOR_VALUE};

        for (int line = 0; line < LINES; line++) {
            int ly = iy + line * lineHeight;
            g.drawString(font, labelText[line], ix, ly, argb(alpha, COLOR_LABEL), false);

            float start = line * 0.13F;
            float end = start + 0.3F;
            float lineReveal = Math.min(1.0F, Math.max(0.0F, (progress - start) / (end - start)));
            String shown = decrypt(values[line], line, lineReveal, now, isLost);
            int color = lineReveal >= 1.0F ? colors[line] : (isLost ? COLOR_LOST : 0x4F6A80);
            int textEnd = g.drawString(font, shown, valueX, ly, argb(alpha, color), false);

            if (line == 3) {
                int barX = Math.max(textEnd + 4, valueX + 28);
                int barW = 40;
                g.fill(barX, ly + 2, barX + barW, ly + 6, argb(alpha, 0x1B232C));
                int fill = (int) (barW * data.stability() / 100.0F * lineReveal);
                g.fill(barX, ly + 2, barX + fill, ly + 6, argb(alpha, stabilityColor(data.stability())));
            }
            if (line == 5 && lineReveal >= 1.0F) {
                int pips = Math.min(data.usesRemaining(), 12);
                int px = textEnd + 5;
                for (int i = 0; i < pips; i++) {
                    g.fill(px + i * 4, ly + 2, px + i * 4 + 2, ly + 6, argb(alpha, data.color().rgb()));
                }
            }
        }
    }

    private static String decrypt(String value, int line, float reveal, float now, boolean isLost) {
        StringBuilder builder = new StringBuilder(value.length());
        int tick = isLost ? (int) (lostTick / 2) : (int) (now / 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            float threshold = noise(line, i, 3) * 0.85F;
            if (c == ' ' || reveal > threshold + 0.1F || reveal >= 1.0F) {
                builder.append(c);
            } else {
                builder.append(GLYPHS.charAt((int) (noise(line * 31 + i, tick, 5) * GLYPHS.length()) % GLYPHS.length()));
            }
        }
        return builder.toString();
    }

    private static int stabilityColor(float stability) {
        if (stability > 50.0F) {
            return 0x5CFF7A;
        }
        return stability > 20.0F ? 0xFFE14B : 0xFF4B4B;
    }

    private static float noise(int a, int b, int salt) {
        int h = a * 73856093 ^ b * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65536.0F;
    }

    private static void ring(GuiGraphics g, int x0, int y0, int x1, int y1, int thickness, int color) {
        if (thickness <= 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        g.fill(x0, y0, x1, y0 + thickness, color);
        g.fill(x0, y1 - thickness, x1, y1, color);
        g.fill(x0, y0 + thickness, x0 + thickness, y1 - thickness, color);
        g.fill(x1 - thickness, y0 + thickness, x1, y1 - thickness, color);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
