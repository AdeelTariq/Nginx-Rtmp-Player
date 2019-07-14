package com.winterparadox.nginxplayer.monitorwall;


import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import com.winterparadox.nginxplayer.common.Stream;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
 * <p>
 * The object is created on the render thread, and the various "send" methods are called
 * from the UI thread.
 */
class RenderHandler extends Handler {
    private static final int MSG_SURFACE_AVAILABLE = 0;
    private static final int MSG_SURFACE_CHANGED = 1;
    private static final int MSG_SURFACE_DESTROYED = 2;
    private static final int MSG_SHUTDOWN = 3;
    private static final int MSG_FRAME_AVAILABLE = 4;
    private static final int MSG_SUBMIT_STREAMS = 5;
    private static final int MSG_POSITION = 8;
    private static final int MSG_REDRAW = 9;

    // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
    // but no real harm in it.
    private WeakReference<RenderThread> mWeakRenderThread;

    /**
     * Call from render thread.
     */
    public RenderHandler(RenderThread rt) {
        mWeakRenderThread = new WeakReference<>(rt);
    }

    /**
     * Sends the "surface available" message.  If the surface was newly created (i.e.
     * this is called from surfaceCreated()), set newSurface to true.  If this is
     * being called during Activity startup for a previously-existing surface, set
     * newSurface to false.
     * <p>
     * The flag tells the caller whether or not it can expect a surfaceChanged() to
     * arrive very soon.
     * <p>
     * Call from UI thread.
     */
    public void sendSurfaceAvailable(SurfaceHolder holder, boolean newSurface) {
        sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,
                newSurface ? 1 : 0, 0, holder));
    }

    /**
     * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
     * <p>
     * Call from UI thread.
     */
    public void sendSurfaceChanged(@SuppressWarnings("unused") int format, int width,
                                   int height) {
        // ignore format
        sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
    }

    /**
     * Sends the "shutdown" message, which tells the render thread to halt.
     * <p>
     * Call from UI thread.
     */
    public void sendSurfaceDestroyed() {
        sendMessage(obtainMessage(MSG_SURFACE_DESTROYED));
    }

    /**
     * Sends the "shutdown" message, which tells the render thread to halt.
     * <p>
     * Call from UI thread.
     */
    public void sendShutdown() {
        sendMessage(obtainMessage(MSG_SHUTDOWN));
    }

    /**
     * Sends the "frame available" message.
     * <p>
     * Call from UI thread.
     */
    public void sendFrameAvailable() {
        sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
    }

    /**
     * Sends the "position" message.  Sets the position of the rect.
     * <p>
     * Call from UI thread.
     */
    public void sendPosition(int x, int y) {
        sendMessage(obtainMessage(MSG_POSITION, x, y));
    }

    /**
     * Sends the "redraw" message.  Forces an immediate redraw.
     * <p>
     * Call from UI thread.
     */
    public void sendRedraw() {
        sendMessage(obtainMessage(MSG_REDRAW));
    }

    @Override  // runs on RenderThread
    public void handleMessage(Message msg) {
        int what = msg.what;
        //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

        RenderThread renderThread = mWeakRenderThread.get();
        if (renderThread == null) {
            Timber.i("RenderHandler.handleMessage: weak ref is null");
            return;
        }

        switch (what) {
            case MSG_SURFACE_AVAILABLE:
                renderThread.surfaceAvailable((SurfaceHolder) msg.obj, msg.arg1 != 0);
                break;
            case MSG_SURFACE_CHANGED:
                renderThread.surfaceChanged(msg.arg1, msg.arg2);
                break;
            case MSG_SURFACE_DESTROYED:
                renderThread.surfaceDestroyed();
                break;
            case MSG_SHUTDOWN:
                renderThread.shutdown();
                break;
            case MSG_FRAME_AVAILABLE:
                renderThread.frameAvailable();
                break;
            case MSG_POSITION:
                renderThread.setTouchPosition(msg.arg1, msg.arg2);
                break;
            case MSG_REDRAW:
                renderThread.draw();
                break;
            case MSG_SUBMIT_STREAMS:
                renderThread.setStreams ((List<Stream>) msg.obj);
                break;
            default:
                throw new RuntimeException("unknown message " + what);
        }
    }

    public void submitStreams(@NotNull List<Stream> streams) {
        sendMessage(obtainMessage(MSG_SUBMIT_STREAMS, streams));
    }
}
