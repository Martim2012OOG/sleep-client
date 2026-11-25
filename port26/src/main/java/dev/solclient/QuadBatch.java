package dev.solclient;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

/**
 * Many solid quads sent to the GUI as ONE element. Since 1.21.6 every GUI element is checked against all the others already
 * added (to batch draws), so the cost grows with the square of the number of elements: a rounded box made of 7-100 fills
 * made the Caos menu lose ~200 FPS. One rounded box / circle / outline is now one element. Same pipeline and look as
 * {@link GuiGraphicsExtractor#fill}.
 */
final class QuadBatch implements GuiElementRenderState {

	private int[] q = new int[5 * 32];
	private int n;
	private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
	private Matrix3x2f pose;
	private ScreenRectangle scissor, bounds;

	/** Same rules as DrawContext.fill: corners in any order, empty quads are skipped. */
	void add(int x0, int y0, int x1, int y1, int color) {
		if (x0 > x1) {
			int t = x0;
			x0 = x1;
			x1 = t;
		}
		if (y0 > y1) {
			int t = y0;
			y0 = y1;
			y1 = t;
		}
		if (x0 == x1 || y0 == y1 || (color >>> 24) == 0) {
			return;
		}
		if ((n + 1) * 5 > q.length) {
			q = java.util.Arrays.copyOf(q, q.length * 2);
		}
		int i = n++ * 5;
		q[i] = x0;
		q[i + 1] = y0;
		q[i + 2] = x1;
		q[i + 3] = y1;
		q[i + 4] = color;
		minX = Math.min(minX, x0);
		minY = Math.min(minY, y0);
		maxX = Math.max(maxX, x1);
		maxY = Math.max(maxY, y1);
	}

	/** Hands the quads to the GUI (with the current matrix and scissor). The batch must not be reused afterwards. */
	void submit(GuiGraphicsExtractor c) {
		if (n == 0) {
			return;
		}
		pose = new Matrix3x2f(c.pose());
		scissor = c.scissorStack.peek();
		ScreenRectangle r = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
		bounds = scissor != null ? scissor.intersection(r) : r;
		if (bounds == null) {
			return; // fully clipped away
		}
		c.guiRenderState.addGuiElement(this);
	}

	@Override
	public void buildVertices(VertexConsumer v) {
		for (int k = 0; k < n; k++) {
			int i = k * 5;
			float x0 = q[i], y0 = q[i + 1], x1 = q[i + 2], y1 = q[i + 3];
			int col = q[i + 4];
			v.addVertexWith2DPose(pose, x0, y0).setColor(col);
			v.addVertexWith2DPose(pose, x0, y1).setColor(col);
			v.addVertexWith2DPose(pose, x1, y1).setColor(col);
			v.addVertexWith2DPose(pose, x1, y0).setColor(col);
		}
	}

	@Override
	public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public ScreenRectangle scissorArea() {
		return scissor;
	}

	@Override
	public ScreenRectangle bounds() {
		return bounds;
	}
}
