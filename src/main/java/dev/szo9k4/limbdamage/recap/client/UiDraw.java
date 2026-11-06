package dev.szo9k4.limbdamage.recap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * A tiny immediate-mode vector painter for the damage HUD. Everything (panels, strips, markers, the body
 * figure) goes into ONE triangle batch and is sent to the GPU with a single draw call, instead of one
 * GuiGraphics.fill per pixel block.
 *
 * Coordinates are in screen pixels: the caller scales the pose by 1 / guiScale, so one unit here is one real
 * pixel. Straight edges placed on whole pixels stay perfectly hard; only curved and diagonal edges (rounded
 * corners, the head, diamonds) get a one-pixel feathered rim, which is what anti-aliasing is.
 */
final class UiDraw {

    /** Width of the soft rim around curved edges, in pixels. */
    private static final float FEATHER = 1.0f;

    private final BufferBuilder buffer;
    private final Matrix4f pose;
    private int vertices;

    private UiDraw(BufferBuilder buffer, Matrix4f pose) {
        this.buffer = buffer;
        this.pose = pose;
    }

    static UiDraw begin(Matrix4f pose) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return new UiDraw(builder, pose);
    }

    void end() {
        BufferBuilder.RenderedBuffer rendered = buffer.end();
        if (vertices > 0) {
            BufferUploader.drawWithShader(rendered);
        } else {
            rendered.release();
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ low level

    private void vertex(float x, float y, int argb) {
        buffer.vertex(pose, x, y, 0f)
                .color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24)
                .endVertex();
        vertices++;
    }

    private void triangle(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        vertex(x0, y0, c0);
        vertex(x1, y1, c1);
        vertex(x2, y2, c2);
    }

    // ------------------------------------------------------------------ hard-edged shapes

    void rect(float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || (argb >>> 24) == 0) {
            return;
        }
        triangle(x0, y0, argb, x0, y1, argb, x1, y1, argb);
        triangle(x0, y0, argb, x1, y1, argb, x1, y0, argb);
    }

    void gradientV(float x0, float y0, float x1, float y1, int top, int bottom) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        triangle(x0, y0, top, x0, y1, bottom, x1, y1, bottom);
        triangle(x0, y0, top, x1, y1, bottom, x1, y0, top);
    }

    /** A hollow rectangle with a border of the given thickness (pixels), inside the box. */
    void frame(float x0, float y0, float x1, float y1, float thickness, int argb) {
        rect(x0, y0, x1, y0 + thickness, argb);
        rect(x0, y1 - thickness, x1, y1, argb);
        rect(x0, y0 + thickness, x0 + thickness, y1 - thickness, argb);
        rect(x1 - thickness, y0 + thickness, x1, y1 - thickness, argb);
    }

    // ------------------------------------------------------------------ anti-aliased shapes

    /** Rectangle with rounded corners: hard straight edges, feathered corner arcs. */
    void roundRect(float x, float y, float w, float h, float radius, int argb) {
        float r = Math.min(radius, Math.min(w, h) / 2f);
        if (r < 0.75f) {
            rect(x, y, x + w, y + h, argb);
            return;
        }
        rect(x + r, y, x + w - r, y + h, argb);
        rect(x, y + r, x + r, y + h - r, argb);
        rect(x + w - r, y + r, x + w, y + h - r, argb);
        arc(x + r, y + r, r, 180f, 270f, argb);
        arc(x + w - r, y + r, r, 270f, 360f, argb);
        arc(x + w - r, y + h - r, r, 0f, 90f, argb);
        arc(x + r, y + h - r, r, 90f, 180f, argb);
    }

    /** Filled ellipse with a feathered rim. */
    void ellipse(float cx, float cy, float rx, float ry, int argb) {
        int segments = Math.max(12, (int) Math.ceil((rx + ry) * 1.6f));
        int clear = argb & 0x00FFFFFF;
        double step = Math.PI * 2.0 / segments;
        for (int i = 0; i < segments; i++) {
            double a0 = i * step;
            double a1 = (i + 1) * step;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ax = cx + c0 * rx, ay = cy + s0 * ry;
            float bx = cx + c1 * rx, by = cy + s1 * ry;
            triangle(cx, cy, argb, ax, ay, argb, bx, by, argb);
            float ox0 = cx + c0 * (rx + FEATHER), oy0 = cy + s0 * (ry + FEATHER);
            float ox1 = cx + c1 * (rx + FEATHER), oy1 = cy + s1 * (ry + FEATHER);
            triangle(ax, ay, argb, ox0, oy0, clear, ox1, oy1, clear);
            triangle(ax, ay, argb, ox1, oy1, clear, bx, by, argb);
        }
    }

    /** Filled diamond (a square turned 45 degrees) with a feathered rim. */
    void diamond(float cx, float cy, float radius, int argb) {
        int clear = argb & 0x00FFFFFF;
        float[] px = {cx, cx + radius, cx, cx - radius};
        float[] py = {cy - radius, cy, cy + radius, cy};
        triangle(px[0], py[0], argb, px[1], py[1], argb, px[2], py[2], argb);
        triangle(px[0], py[0], argb, px[2], py[2], argb, px[3], py[3], argb);
        float f = FEATHER * 1.4142f;
        float[] ox = {cx, cx + radius + f, cx, cx - radius - f};
        float[] oy = {cy - radius - f, cy, cy + radius + f, cy};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            triangle(px[i], py[i], argb, ox[i], oy[i], clear, ox[j], oy[j], clear);
            triangle(px[i], py[i], argb, ox[j], oy[j], clear, px[j], py[j], argb);
        }
    }

    /** A pie slice from a0 to a1 degrees (screen space, y down) with a feathered outer rim. */
    private void arc(float cx, float cy, float r, float a0Deg, float a1Deg, int argb) {
        int segments = Math.max(3, (int) Math.ceil(r * 0.9f));
        int clear = argb & 0x00FFFFFF;
        double a0 = Math.toRadians(a0Deg);
        double step = Math.toRadians(a1Deg - a0Deg) / segments;
        for (int i = 0; i < segments; i++) {
            double s = a0 + i * step;
            double e = s + step;
            float cs = (float) Math.cos(s), ss = (float) Math.sin(s);
            float ce = (float) Math.cos(e), se = (float) Math.sin(e);
            float ax = cx + cs * r, ay = cy + ss * r;
            float bx = cx + ce * r, by = cy + se * r;
            triangle(cx, cy, argb, ax, ay, argb, bx, by, argb);
            float ox0 = cx + cs * (r + FEATHER), oy0 = cy + ss * (r + FEATHER);
            float ox1 = cx + ce * (r + FEATHER), oy1 = cy + se * (r + FEATHER);
            triangle(ax, ay, argb, ox0, oy0, clear, ox1, oy1, clear);
            triangle(ax, ay, argb, ox1, oy1, clear, bx, by, argb);
        }
    }

    // ------------------------------------------------------------------ colour helpers

    static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f + 0.5f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
