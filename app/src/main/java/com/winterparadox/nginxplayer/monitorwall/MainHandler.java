package com.winterparadox.nginxplayer.monitorwall;


import android.os.Handler;
import android.os.Message;
import timber.log.Timber;

import java.lang.ref.WeakReference;

/**
 * Custom message handler for main UI thread.
 * <p>
 * Receives messages from the renderer thread with UI-related updates, like the camera
 * parameters (which we show in a text message on screen).
 */
class MainHandler extends Handler {
    private static final int MSG_SEND_CAMERA_PARAMS0 = 0;
    private static final int MSG_SEND_READY = 1;

    private WeakReference<SurfaceViewParent> mWeakParent;

    MainHandler(SurfaceViewParent parent) {
        mWeakParent = new WeakReference<>(parent);
    }

    /**
     * Sends the updated camera parameters to the main thread.
     * <p>
     * Call from render thread.
     */
    public void sendCameraParams(int width, int height) {
        // The right way to do this is to bundle them up into an object.  The lazy
        // way is to send two messages.
        sendMessage(obtainMessage(MSG_SEND_CAMERA_PARAMS0, width, height));
    }

    @Override
    public void handleMessage(Message msg) {
        SurfaceViewParent parent = mWeakParent.get();
        if (parent == null) {
            Timber.i("Got message for dead parent");
            return;
        }

        switch (msg.what) {
            case MSG_SEND_CAMERA_PARAMS0: {
                parent.showPreviewSize(msg.arg1, msg.arg2);
                break;
            }
            case MSG_SEND_READY: {
                parent.onDisplayReady();
                break;
            }
            default:
                throw new RuntimeException("Unknown message " + msg.what);
        }
    }

    public void sendReady() {
        sendMessage(obtainMessage(MSG_SEND_READY));
    }
}

