package wikidiosesmon.viewer;

import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFClient;
import org.cef.browser.CefBrowser;

import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;

/**
 * MCEF browser with three fixes:
 * <ul>
 *   <li>Remembers whether Chromium has drawn anything yet (the texture is empty before).</li>
 *   <li>While hidden, frames are not uploaded to the GPU at all, so a closed page that still
 *       animates costs no FPS; showing it again asks Chromium for one full repaint.</li>
 *   <li>Ends drag and drop properly. MCEF only tells the drop target, never the drag source, so
 *       Chromium thinks the first drag never finished and blocks every later one (dragging
 *       berries into the pot in Bongurchatas stopped working after the first try).</li>
 * </ul>
 */
final class WikiBrowser extends MCEFBrowser {
    /** CEF's DRAG_OPERATION_NONE. */
    private static final int DRAG_NONE = 0;

    volatile boolean painted;
    /** Statistics for the development autotest: frames uploaded and time spent uploading. */
    static long paintNanos;
    static int paintCount;
    private volatile boolean hidden;

    WikiBrowser(MCEFClient client, String url) {
        super(client, url, false);
    }

    void setHidden(boolean value) {
        if (hidden == value) {
            return;
        }
        hidden = value;
        if (!value) {
            // Frames skipped while hidden left the texture stale: repaint the whole page once.
            invalidate();
        }
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (hidden) {
            return;
        }
        long t = System.nanoTime();
        super.onPaint(browser, popup, dirtyRects, buffer, width, height);
        paintNanos += System.nanoTime() - t;
        paintCount++;
        if (!popup) {
            painted = true;
        }
    }

    @Override
    public void finishDragging(int x, int y) {
        boolean wasDragging = getDragContext().isDragging();
        int mask = getDragContext().getMask();
        super.finishDragging(x, y);
        if (wasDragging) {
            dragSourceEndedAt(new Point(x, y), mask);
            dragSourceSystemDragEnded();
        }
    }

    @Override
    public void cancelDrag() {
        boolean wasDragging = getDragContext().isDragging();
        super.cancelDrag();
        if (wasDragging) {
            dragSourceEndedAt(new Point(0, 0), DRAG_NONE);
            dragSourceSystemDragEnded();
        }
    }
}
