package com.ectex.plugin.views;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Control;

/**
 * Helpers for the experimental "sharp text on high-DPI screens" rendering of the PDF Preview.
 * <p>
 * Normally a page is rendered with one bitmap pixel per logical pixel and Eclipse stretches it on a scaled
 * screen (125%, 150%, 200%), which makes text soft. Here the page is rendered with as many pixels as the screen
 * really has and handed to SWT together with the zoom it was made for, so SWT draws it 1:1.
 */
final class HiDpiImages {

	/** Pixels per PDF point we never exceed, so that one page bitmap stays below about 32 MB. */
	static final double MAX_PIXEL_SCALE = 4.0;

	/** {@code Monitor.getZoom()}; looked up by reflection because older SWT versions do not have it. */
	private static final Method GET_ZOOM = findGetZoom();

	private HiDpiImages() {
	}

	private static Method findGetZoom() {
		try {
			return org.eclipse.swt.widgets.Monitor.class.getMethod("getZoom");
		} catch (NoSuchMethodException | RuntimeException e) {
			return null;
		}
	}

	/** The zoom of the screen the control is on, in percent (100, 125, 150, 200, ...); 100 if it cannot be read. */
	static int zoomOf(Control control) {
		if (GET_ZOOM == null || control == null || control.isDisposed()) {
			return 100;
		}
		try {
			Object zoom = GET_ZOOM.invoke(control.getMonitor());
			return zoom instanceof Integer value && value >= 100 ? value : 100;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 100;
		}
	}

	/**
	 * The zoom (percent) to render a page at: the screen's zoom, but never below 100 and never so high that the
	 * pixel scale (logical scale times zoom) exceeds {@link #MAX_PIXEL_SCALE}.
	 */
	static int renderZoom(int screenZoom, double logicalScale) {
		int limit = (int) Math.floor(MAX_PIXEL_SCALE / logicalScale * 100);
		return Math.max(100, Math.min(screenZoom, limit));
	}

	/**
	 * The rendered page as SWT image data of exactly {@code logicalWidth * zoom / 100} by
	 * {@code logicalHeight * zoom / 100} pixels (rounded): a bitmap that is a pixel too large is cropped, one that
	 * is too small is padded with white. This keeps the logical size of the image equal to the page size used by the
	 * layout.
	 */
	static ImageData toImageData(BufferedImage image, int logicalWidth, int logicalHeight, int zoom) {
		int width = Math.max(1, Math.round(logicalWidth * zoom / 100f));
		int height = Math.max(1, Math.round(logicalHeight * zoom / 100f));
		ImageData data = new ImageData(width, height, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
		int[] row = new int[width];
		int copyWidth = Math.min(width, image.getWidth());
		for (int y = 0; y < height; y++) {
			Arrays.fill(row, 0xFFFFFF);
			if (y < image.getHeight()) {
				image.getRGB(0, y, copyWidth, 1, row, 0, width);
			}
			data.setPixels(0, y, width, row, 0);
		}
		return data;
	}

	/**
	 * An SWT image for page data rendered at {@code zoom} percent. At 100% this is a plain image. Otherwise the
	 * image is created through an {@link ImageDataProvider}: SWT gets the data as is when it needs it at that zoom
	 * (the normal case), and a resized copy if it ever asks for another zoom, e.g. after the window moved to a
	 * screen with different scaling.
	 */
	static Image createImage(Device device, ImageData data, int zoom) {
		if (zoom <= 100) {
			return new Image(device, data);
		}
		return new Image(device, (ImageDataProvider) requested -> requested == zoom ? data
				: data.scaledTo(Math.max(1, Math.round(data.width * requested / (float) zoom)),
						Math.max(1, Math.round(data.height * requested / (float) zoom))));
	}

	/** Approximate memory used by an image created with {@link #createImage}: the data is kept for the provider. */
	static long bytes(ImageData data, int zoom) {
		return (long) data.data.length * (zoom > 100 ? 2 : 1);
	}
}
