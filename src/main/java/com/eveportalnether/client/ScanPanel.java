package com.eveportalnether.client;

import com.eveportalnether.EvePortalNether;
import com.eveportalnether.network.PortalScanPayload;
import com.mojang.blaze3d.systems.RenderSystem;
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
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_frame.png");
    private static final ResourceLocation FRAME_SEAM_TEXTURE = ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_frame_seams.png");
    private static final ResourceLocation WINDOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(EvePortalNether.MODID, "textures/block/wormhole_center.png");
    private static final int WINDOW_FRAMES = 32;

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
        int cell = Math.max(3, Math.min((bw - 8) / w, (bh - 8) / h));
        int pw = cell * w;
        int ph = cell * h;
        int ox = bx + (bw - pw) / 2;
        int oy = by + (bh - ph) / 2;

        int tint = data.color().tint(data.stage() < 0 ? 3 : data.stage());
        g.fill(ox + cell, oy + cell, ox + pw - cell, oy + ph - cell, argb(alpha * 0.3F, tint));

        RenderSystem.enableBlend();
        float flicker = data.stage() >= 2 ? 0.75F + 0.25F * (float) Math.sin(now * 0.9F) : 1.0F;
        g.setColor(((tint >> 16) & 0xFF) / 255.0F * flicker, ((tint >> 8) & 0xFF) / 255.0F * flicker, (tint & 0xFF) / 255.0F * flicker, alpha);
        int frame = ((int) (now / 2)) % WINDOW_FRAMES;
        for (int cx = 1; cx < w - 1; cx++) {
            for (int cy = 1; cy < h - 1; cy++) {
                g.blit(WINDOW_TEXTURE, ox + cx * cell, oy + cy * cell, cell, cell, 0, frame * 16, 16, 16, 16, 16 * WINDOW_FRAMES);
            }
        }
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                if (cx == 0 || cy == 0 || cx == w - 1 || cy == h - 1) {
                    g.blit(FRAME_TEXTURE, ox + cx * cell, oy + cy * cell, cell, cell, 0, 0, 16, 16, 16, 16);
                }
            }
        }
        g.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, alpha);
        for (int cx = 0; cx < w; cx++) {
            for (int cy = 0; cy < h; cy++) {
                if (cx == 0 || cy == 0 || cx == w - 1 || cy == h - 1) {
                    g.blit(FRAME_SEAM_TEXTURE, ox + cx * cell, oy + cy * cell, cell, cell, 0, 0, 16, 16, 16, 16);
                }
            }
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

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

    private static int argb(float alpha, int rgb) {
        int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
