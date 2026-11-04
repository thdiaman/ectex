package com.ectex.plugin.views;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ScrollBar;

import com.ectex.plugin.Activator;

/**
 * A minimal PDF viewer: pages are stacked vertically and rendered lazily with PDFBox on a
 * background thread. Mouse wheel scrolls, Ctrl+wheel zooms, and the viewer exposes
 * page coordinates (PDF points from the top-left corner) for SyncTeX.
 */
public class PdfViewer extends Canvas {

	/** A position on a page; {@code page} is 1-based, x/y are PDF points from the top-left corner. */
	public record PdfPoint(int page, double x, double y) {
	}

	private record Highlight(int pageIndex, double x, double y, double width, double height) {
	}

	/** Loaded document plus page sizes in PDF points (rotation applied). */
	private static final class DocState {
		final PDDocument document;
		final PDFRenderer renderer;
		final double[] widths;
		final double[] heights;

		DocState(PDDocument document) {
			this.document = document;
			this.renderer = new PDFRenderer(document);
			int n = document.getNumberOfPages();
			widths = new double[n];
			heights = new double[n];
			for (int i = 0; i < n; i++) {
				PDPage page = document.getPage(i);
				PDRectangle box = page.getCropBox();
				boolean sideways = page.getRotation() % 180 != 0;
				widths[i] = sideways ? box.getHeight() : box.getWidth();
				heights[i] = sideways ? box.getWidth() : box.getHeight();
			}
		}
	}

	private record CachedPage(Image image, double scale) {
	}

	private static final int MARGIN = 12;
	private static final int GAP = 12;
	private static final int MAX_CACHED_PAGES = 16;
	private static final double MIN_SCALE = 0.25;
	private static final double MAX_SCALE = 4.0;
	private static final int HIGHLIGHT_MILLIS = 4000;

	private final ExecutorService renderPool = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "EcTeX PDF renderer");
		thread.setDaemon(true);
		// commons-logging (used by PDFBox) looks classes up through the context class loader.
		thread.setContextClassLoader(PdfViewer.class.getClassLoader());
		return thread;
	});

	// UI-thread state
	private final Map<Integer, CachedPage> cache = new LinkedHashMap<>(16, 0.75f, true);
	private final Set<String> pending = new HashSet<>();
	private final Set<Integer> failed = new HashSet<>();
	private volatile DocState state;
	private volatile double scale = 1.0;
	private boolean fitWidth = true;
	private int[] pageTop = new int[0];
	private int contentWidth;
	private int contentHeight;
	private Point lastClick;
	private Highlight highlight;
	private Supplier<String> placeholder = () -> "No PDF to show.";
	private String message = placeholder.get();
	private Consumer<PdfPoint> inverseSearchHandler;
	private Runnable pendingReveal;

	public PdfViewer(Composite parent) {
		super(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.DOUBLE_BUFFERED);
		setBackground(getDisplay().getSystemColor(SWT.COLOR_GRAY));

		addPaintListener(e -> paint(e.gc));
		addListener(SWT.Resize, e -> relayout(fitWidth));
		getVerticalBar().addListener(SWT.Selection, e -> redraw());
		getHorizontalBar().addListener(SWT.Selection, e -> redraw());
		addListener(SWT.MouseDown, e -> {
			setFocus();
			lastClick = new Point(e.x, e.y);
			// Ctrl+click = inverse search
			if (e.button == 1 && (e.stateMask & SWT.MOD1) != 0 && inverseSearchHandler != null && state != null
					&& pageTop.length > 0) {
				inverseSearchHandler.accept(toPdfPoint(e.x, e.y));
			}
		});
		addListener(SWT.MouseWheel, this::onMouseWheel);
		addListener(SWT.KeyDown, this::onKeyDown);
		addDisposeListener(e -> onDispose());
		createContextMenu();
	}

	// ---------------------------------------------------------------- public API

	/** Loads (or reloads) a PDF. The file is read into memory so it is never locked while pdflatex rewrites it. */
	public void load(File pdf) {
		Display display = getDisplay();
		try {
			renderPool.execute(() -> {
				try {
					byte[] bytes = Files.readAllBytes(pdf.toPath());
					DocState loaded = new DocState(PDDocument.load(bytes));
					display.asyncExec(() -> install(loaded));
				} catch (IOException | RuntimeException e) {
					Activator.log("Cannot load PDF " + pdf, e);
					display.asyncExec(() -> {
						if (!isDisposed() && state == null) {
							message = "Cannot display PDF: " + e.getMessage();
							redraw();
						}
					});
				}
			});
		} catch (RejectedExecutionException e) {
			// viewer already disposed
		}
	}

	/** Called (in the UI thread) when the user Ctrl+clicks a position in the PDF. */
	public void setInverseSearchHandler(Consumer<PdfPoint> handler) {
		this.inverseSearchHandler = handler;
	}

	/** Text shown while there is no PDF; evaluated each time the viewer is cleared. */
	public void setPlaceholder(Supplier<String> placeholder) {
		this.placeholder = placeholder;
		if (state == null) {
			message = placeholder.get();
			redraw();
		}
	}

	/** Shows the "no PDF" placeholder. */
	public void clear() {
		DocState old = state;
		state = null;
		pendingReveal = null;
		message = placeholder.get();
		clearCache();
		if (old != null) {
			closeLater(old);
		}
		relayout(false);
	}

	/** Scrolls to a SyncTeX result (1-based page, PDF points from top-left) and highlights it. */
	public void reveal(int page, double x, double y, double width, double height) {
		DocState st = state;
		if (st == null) {
			// The document is still loading: apply once it is installed.
			pendingReveal = () -> reveal(page, x, y, width, height);
			return;
		}
		if (page < 1 || page > st.widths.length) {
			return;
		}
		int index = page - 1;
		Highlight marker = new Highlight(index, x, y, Math.max(width, 4), Math.max(height, 8));
		highlight = marker;

		Rectangle client = getClientArea();
		int targetY = pageTop[index] + (int) Math.round(y * scale);
		getVerticalBar().setSelection(targetY - client.height / 3);
		if (contentWidth > client.width) {
			int areaLeft = (contentWidth - pageWidthPx(index)) / 2;
			getHorizontalBar().setSelection(areaLeft + (int) Math.round(x * scale) - client.width / 2);
		}
		redraw();
		getDisplay().timerExec(HIGHLIGHT_MILLIS, () -> {
			if (!isDisposed() && highlight == marker) {
				highlight = null;
				redraw();
			}
		});
	}

	/**
	 * The PDF position under the mouse cursor, or - if the cursor is outside the viewer - the
	 * last clicked position. Null if there is no document or nothing was clicked yet.
	 */
	public PdfPoint getPointAtCursor() {
		if (state == null || pageTop.length == 0) {
			return null;
		}
		Point p = toControl(getDisplay().getCursorLocation());
		if (!getClientArea().contains(p)) {
			p = lastClick;
		}
		return p == null ? null : toPdfPoint(p.x, p.y);
	}

	// ---------------------------------------------------------------- state changes

	private void install(DocState loaded) {
		if (isDisposed()) {
			closeLater(loaded);
			return;
		}
		DocState old = state;
		state = loaded;
		message = null;
		clearCache();
		if (old != null) {
			closeLater(old);
		}
		relayout(false);
		Runnable reveal = pendingReveal;
		pendingReveal = null;
		if (reveal != null) {
			reveal.run();
		}
	}

	private void clearCache() {
		for (CachedPage page : cache.values()) {
			page.image().dispose();
		}
		cache.clear();
		pending.clear();
		failed.clear();
	}

	private void closeLater(DocState st) {
		try {
			// Serialised behind running renders so a page is never rendered from a closed document.
			renderPool.execute(() -> {
				try {
					st.document.close();
				} catch (IOException e) {
					Activator.log("Cannot close PDF document", e);
				}
			});
		} catch (RejectedExecutionException e) {
			// pool already shut down
		}
	}

	private void onDispose() {
		DocState st = state;
		state = null;
		clearCache();
		if (st != null) {
			closeLater(st);
		}
		renderPool.shutdown();
	}

	// ---------------------------------------------------------------- layout

	private int pageWidthPx(int i) {
		return (int) Math.round(state.widths[i] * scale);
	}

	private int pageHeightPx(int i) {
		return (int) Math.round(state.heights[i] * scale);
	}

	private int pageLeft(int i) {
		int areaWidth = Math.max(contentWidth, getClientArea().width);
		return (areaWidth - pageWidthPx(i)) / 2 - getHorizontalBar().getSelection();
	}

	/**
	 * Recomputes page positions and scrollbars.
	 *
	 * @param keepCentre keep the document point in the middle of the viewport fixed (used for zooming/resizing)
	 */
	private void relayout(boolean keepCentre) {
		if (isDisposed()) {
			return;
		}
		Rectangle client = getClientArea();
		ScrollBar vBar = getVerticalBar();
		ScrollBar hBar = getHorizontalBar();
		double ry = contentHeight > 0 ? (vBar.getSelection() + client.height / 2.0) / contentHeight : 0;
		double rx = contentWidth > 0 ? (hBar.getSelection() + client.width / 2.0) / contentWidth : 0;

		DocState st = state;
		if (st == null || st.widths.length == 0) {
			pageTop = new int[0];
			contentWidth = 0;
			contentHeight = 0;
		} else {
			double maxWidth = 0;
			for (double w : st.widths) {
				maxWidth = Math.max(maxWidth, w);
			}
			if (fitWidth && client.width > 2 * MARGIN + 10) {
				scale = clamp((client.width - 2.0 * MARGIN) / maxWidth);
			}
			pageTop = new int[st.widths.length];
			int y = MARGIN;
			for (int i = 0; i < pageTop.length; i++) {
				pageTop[i] = y;
				y += pageHeightPx(i) + GAP;
			}
			contentHeight = y - GAP + MARGIN;
			contentWidth = (int) Math.ceil(maxWidth * scale) + 2 * MARGIN;
		}

		setBar(vBar, vBar.getSelection(), contentHeight, client.height);
		setBar(hBar, hBar.getSelection(), contentWidth, client.width);
		if (keepCentre) {
			vBar.setSelection((int) Math.round(ry * contentHeight - client.height / 2.0));
			hBar.setSelection((int) Math.round(rx * contentWidth - client.width / 2.0));
		}
		redraw();
	}

	private static void setBar(ScrollBar bar, int selection, int content, int visible) {
		int max = Math.max(content, 1);
		int thumb = Math.max(1, Math.min(visible, max));
		bar.setValues(Math.max(0, Math.min(selection, max - thumb)), 0, max, thumb, 40, Math.max(thumb - 40, 1));
	}

	private static double clamp(double value) {
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
	}

	private void zoomBy(double factor) {
		if (state == null) {
			return;
		}
		fitWidth = false;
		scale = clamp(scale * factor);
		relayout(true);
	}

	private void fitToWidth() {
		fitWidth = true;
		relayout(true);
	}

	// ---------------------------------------------------------------- painting and rendering

	private void paint(GC gc) {
		Rectangle client = getClientArea();
		gc.setBackground(getBackground());
		gc.fillRectangle(client);

		DocState st = state;
		if (st == null) {
			if (message != null) {
				int flags = SWT.DRAW_DELIMITER | SWT.DRAW_TRANSPARENT;
				Point extent = gc.textExtent(message, flags);
				gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
				gc.drawText(message, Math.max(MARGIN, (client.width - extent.x) / 2),
						Math.max(MARGIN, (client.height - extent.y) / 2), flags);
			}
			return;
		}

		Color white = getDisplay().getSystemColor(SWT.COLOR_WHITE);
		Color border = getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
		int scrollY = getVerticalBar().getSelection();
		for (int i = 0; i < pageTop.length; i++) {
			int top = pageTop[i] - scrollY;
			int height = pageHeightPx(i);
			if (top + height < 0) {
				continue;
			}
			if (top > client.height) {
				break;
			}
			int left = pageLeft(i);
			int width = pageWidthPx(i);

			CachedPage cached = cache.get(i);
			if (cached == null) {
				gc.setBackground(white);
				gc.fillRectangle(left, top, width, height);
			} else if (Math.abs(cached.scale() - scale) < 1e-6) {
				gc.drawImage(cached.image(), left, top);
			} else {
				// Stale zoom level: show the old bitmap stretched until the new one arrives.
				Rectangle b = cached.image().getBounds();
				gc.setInterpolation(SWT.HIGH);
				gc.drawImage(cached.image(), 0, 0, b.width, b.height, left, top, width, height);
			}
			if ((cached == null || Math.abs(cached.scale() - scale) >= 1e-6) && !failed.contains(i)) {
				requestRender(st, i);
			}
			gc.setForeground(border);
			gc.drawRectangle(left - 1, top - 1, width + 1, height + 1);
		}

		Highlight h = highlight;
		if (h != null && h.pageIndex() < pageTop.length) {
			int x = pageLeft(h.pageIndex()) + (int) Math.round(h.x() * scale);
			int y = pageTop[h.pageIndex()] - scrollY + (int) Math.round(h.y() * scale);
			int w = (int) Math.round(h.width() * scale);
			int hh = (int) Math.round(h.height() * scale);
			gc.setAlpha(80);
			gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_YELLOW));
			gc.fillRectangle(x - 2, y - 2, w + 4, hh + 4);
			gc.setAlpha(255);
			gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));
			gc.drawRectangle(x - 2, y - 2, w + 4, hh + 4);
		}
	}

	private void requestRender(DocState st, int index) {
		double renderScale = scale;
		String key = index + "@" + renderScale;
		if (!pending.add(key)) {
			return;
		}
		Display display = getDisplay();
		try {
			renderPool.execute(() -> {
				// Skip work that became obsolete while it was queued (zoomed again, document reloaded).
				if (st != state || Math.abs(renderScale - scale) > 1e-6) {
					display.asyncExec(() -> pending.remove(key));
					return;
				}
				ImageData data = null;
				try {
					BufferedImage image = st.renderer.renderImage(index, (float) renderScale, ImageType.RGB);
					data = toImageData(image);
				} catch (IOException | RuntimeException | LinkageError e) {
					Activator.log("Cannot render PDF page " + (index + 1), e);
				}
				ImageData result = data;
				display.asyncExec(() -> finishRender(st, index, renderScale, key, result));
			});
		} catch (RejectedExecutionException e) {
			pending.remove(key);
		}
	}

	private void finishRender(DocState st, int index, double renderScale, String key, ImageData data) {
		pending.remove(key);
		if (isDisposed() || st != state) {
			return;
		}
		if (data == null) {
			failed.add(index);
			return;
		}
		CachedPage previous = cache.put(index, new CachedPage(new Image(getDisplay(), data), renderScale));
		if (previous != null) {
			previous.image().dispose();
		}
		Iterator<CachedPage> eldest = cache.values().iterator();
		while (cache.size() > MAX_CACHED_PAGES && eldest.hasNext()) {
			eldest.next().image().dispose();
			eldest.remove();
		}
		redraw();
	}

	private static ImageData toImageData(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		ImageData data = new ImageData(width, height, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, row, 0, width);
			data.setPixels(0, y, width, row, 0);
		}
		return data;
	}

	// ---------------------------------------------------------------- coordinates

	private PdfPoint toPdfPoint(int widgetX, int widgetY) {
		DocState st = state;
		int docY = widgetY + getVerticalBar().getSelection();
		int index = pageTop.length - 1;
		for (int i = 0; i < pageTop.length; i++) {
			if (docY < pageTop[i] + pageHeightPx(i) + GAP / 2) {
				index = i;
				break;
			}
		}
		double x = (widgetX - pageLeft(index)) / scale;
		double y = (docY - pageTop[index]) / scale;
		x = Math.max(0, Math.min(st.widths[index], x));
		y = Math.max(0, Math.min(st.heights[index], y));
		return new PdfPoint(index + 1, x, y);
	}

	// ---------------------------------------------------------------- input

	private void onMouseWheel(org.eclipse.swt.widgets.Event e) {
		e.doit = false;
		if ((e.stateMask & SWT.MOD1) != 0) {
			zoomBy(e.count > 0 ? 1.1 : 1 / 1.1);
		} else if ((e.stateMask & SWT.SHIFT) != 0) {
			scrollBy(getHorizontalBar(), -e.count * 40);
		} else {
			scrollBy(getVerticalBar(), -e.count * 40);
		}
	}

	private void onKeyDown(org.eclipse.swt.widgets.Event e) {
		ScrollBar v = getVerticalBar();
		switch (e.keyCode) {
		case SWT.ARROW_DOWN -> scrollBy(v, 40);
		case SWT.ARROW_UP -> scrollBy(v, -40);
		case SWT.PAGE_DOWN -> scrollBy(v, v.getPageIncrement());
		case SWT.PAGE_UP -> scrollBy(v, -v.getPageIncrement());
		case SWT.ARROW_RIGHT -> scrollBy(getHorizontalBar(), 40);
		case SWT.ARROW_LEFT -> scrollBy(getHorizontalBar(), -40);
		case SWT.HOME -> scrollBy(v, -Integer.MAX_VALUE / 2);
		case SWT.END -> scrollBy(v, Integer.MAX_VALUE / 2);
		default -> {
			if (e.character == '+' || e.keyCode == SWT.KEYPAD_ADD) {
				zoomBy(1.1);
			} else if (e.character == '-' || e.keyCode == SWT.KEYPAD_SUBTRACT) {
				zoomBy(1 / 1.1);
			}
		}
		}
	}

	private void scrollBy(ScrollBar bar, int delta) {
		bar.setSelection(bar.getSelection() + delta);
		redraw();
	}

	private void createContextMenu() {
		Menu menu = new Menu(this);
		addMenuItem(menu, "Fit Width", this::fitToWidth);
		addMenuItem(menu, "Zoom In", () -> zoomBy(1.25));
		addMenuItem(menu, "Zoom Out", () -> zoomBy(0.8));
		setMenu(menu);
	}

	private static void addMenuItem(Menu menu, String text, Runnable action) {
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(text);
		item.addListener(SWT.Selection, e -> action.run());
	}
}
