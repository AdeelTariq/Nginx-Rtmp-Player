package com.winterparadox.nginxplayer.monitorwall;


import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.winterparadox.nginxplayer.common.Constants;
import com.winterparadox.nginxplayer.common.Stream;
import com.winterparadox.nginxplayer.gles.*;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread that handles all rendering and stream operations.
 */
class RenderThread extends Thread implements
        SurfaceTexture.OnFrameAvailableListener {

    private static final int MAX_STREAMS = 16;
    // Object must be created on render thread to get correct Looper, but is used from
    // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
    // constructed object.
    private volatile RenderHandler mHandler;

    // Used to wait for the thread to start.
    private final Object mStartLock = new Object();
    private boolean mReady = false;

    private Context context;
    private MainHandler mMainHandler;

    private EglCore mEglCore;
    private WindowSurface mWindowSurface;
    private int mWindowSurfaceWidth;
    private int mWindowSurfaceHeight;

    private Sprite3d textRect;

    // Orthographic projection matrix.
    private float[] streamMatrix = new float[16];

    private Texture2dProgram mTexProgram;
    private Texture2dProgram textProgram;


    /**
     * Constructor.  Pass in the MainHandler, which allows us to send stuff back to the
     * Activity.
     */
    public RenderThread(Context context, MainHandler handler) {
        this.context = context;
        mMainHandler = handler;
    }

    /**
     * Thread entry point.
     */
    @Override
    public void run() {
        Looper.prepare();

        // We need to create the Handler before reporting ready.
        mHandler = new RenderHandler(this);
        synchronized (mStartLock) {
            mReady = true;
            mStartLock.notify();    // signal waitUntilReady()
        }

        // Prepare EGL before we start handling messages.
        mEglCore = new EglCore(null, 0);

        Looper.loop();

        Timber.i("looper quit");

        for (SimpleExoPlayer player : players) {
            player.stop();
            player.release();
        }

        releaseGl();
        mEglCore.release();

        synchronized (mStartLock) {
            mReady = false;
        }
    }

    /**
     * Waits until the render thread is ready to receive messages.
     * <p>
     * Call from the UI thread.
     */
    void waitUntilReady() {
        synchronized (mStartLock) {
            while (!mReady) {
                try {
                    mStartLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    /**
     * Shuts everything down.
     */
    void shutdown() {
        Timber.i("shutdown");
        if (Looper.myLooper() != null) {
            Looper.myLooper().quit();
        }
    }

    /**
     * Returns the render thread's Handler.  This may be called from any thread.
     */
    RenderHandler getHandler() {
        return mHandler;
    }

    /**
     * Releases most of the GL resources we currently hold (anything allocated by
     * surfaceAvailable()).
     * <p>
     * Does not release EglCore.
     */
    private void releaseGl() {
        GlUtil.checkGlError("releaseGl start");

        if (mWindowSurface != null) {
            mWindowSurface.release();
            mWindowSurface = null;
        }
        if (mTexProgram != null) {
            mTexProgram.release();
            mTexProgram = null;
        }
        if (textProgram != null) {
            textProgram.release();
            textProgram = null;
        }
        GlUtil.checkGlError("releaseGl done");

        mEglCore.makeNothingCurrent();
    }

    /**
     * Handles the surfaceChanged message.
     * <p>
     * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
     * could also be called with a Surface created on a previous run.  So this may not
     * be called.
     */
    void surfaceChanged(int width, int height) {
        Timber.i("RenderThread surfaceChanged " + width + "x" + height);

        mWindowSurfaceWidth = width;
        mWindowSurfaceHeight = height;
        finishSurfaceSetup();
    }

    /**
     * Handles the surfaceDestroyed message.
     */
    void surfaceDestroyed() {
        // In practice this never appears to be called -- the activity is always paused
        // before the surface is destroyed.  In theory it could be called though.
        Timber.i("RenderThread surfaceDestroyed");
        releaseGl();
    }

    /**
     * Handles the surface-created callback from SurfaceView.  Prepares GLES and the Surface.
     */
    void surfaceAvailable(SurfaceHolder holder, boolean newSurface) {
        Surface surface = holder.getSurface();
        mWindowSurface = new WindowSurface(mEglCore, surface, false);
        mWindowSurface.makeCurrent();

        // Create and configure the SurfaceTextures, which will receive frames from the
        // streams.  We set the textured rect's program to render from it.
        mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
        textProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D);


        // We are calling this unconditionally, and perhaps do an unnecessary
        // bit of reallocating if a surface-changed message arrives.
        mWindowSurfaceWidth = mWindowSurface.getWidth();
        mWindowSurfaceHeight = mWindowSurface.getHeight();

        if (!newSurface) {
            // This Surface was established on a previous run, so no surfaceChanged()
            // message is forthcoming.  Finish the surface setup now.

            finishSurfaceSetup();
        }

        createText("No Video Feeds Found!");
    }

    /**
     * Sets up anything that depends on the window size.
     * <p>
     * Play the streams
     */
    private void finishSurfaceSetup() {
        int width = mWindowSurfaceWidth;
        int height = mWindowSurfaceHeight;

        // Use full window.
        GLES20.glViewport(0, 0, width, height);

        // Simple orthographic projection, with (0,0) in lower-left corner.
        Matrix.orthoM(streamMatrix, 0, 0, width, 0, height, -1, 1);

        updateGeometry();

        mMainHandler.sendReady ();

    }

    /**
     * Updates the geometry of mRect, based on the size of the window and the current
     * values set by the UI.
     */
    private void updateGeometry() {
        int width = mWindowSurfaceWidth;
        int height = mWindowSurfaceHeight;

        if (streams.size() == 0) {
            clear();
            return;
        }

        // Max scale is a bit larger than the screen, so we can show over-size.
        int numberOfStreams = streams.size();
        if (numberOfStreams != 1) {
            if (numberOfStreams <= 4) {
                numberOfStreams = 4;
            } else if (numberOfStreams <= 9) {
                numberOfStreams = 9;
            } else if (numberOfStreams <= 16) {
                numberOfStreams = 16;
            }
        }

        numberOfStreams = (int) Math.sqrt(numberOfStreams);
        int smallDim = Math.min(width, height);

        float scaled = smallDim * (1f / numberOfStreams);
        float aspectRatio = 1.77777f;

        int newWidth = Math.round(scaled * aspectRatio);
        int newHeight = Math.round(scaled);

        int containerWidth = width / numberOfStreams;

        mMainHandler.sendCameraParams(newWidth, newHeight);

        int posX = (width / numberOfStreams) / 2;
        int posY = (height / numberOfStreams) / 2;

        int row = -1;

        for (int i = 0; i < rects.size(); i++) {
            rects.get(i).setScale(newWidth, newHeight);
            int col = i % numberOfStreams;
            if (col == 0) row++;
            rects.get(i).setPosition(posX + (containerWidth * col), height - (posY + (newHeight * row)));
        }

        draw();
    }

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mHandler.sendFrameAvailable();
    }

    /**
     * Handles incoming frame of data from the stream.
     */
    void frameAvailable() {
        for (SurfaceTexture surfaceTexture : surfaceTextures) {
            surfaceTexture.updateTexImage();
        }
        draw();
    }

    /**
     * Draws the scene and submits the buffer.
     */
    void draw() {

        GlUtil.checkGlError("draw start");

        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        for (Sprite2d rect : rects) {
            rect.draw(mTexProgram, streamMatrix);
        }

        mWindowSurface.swapBuffers();

        GlUtil.checkGlError("draw done");
    }

    /**
     * Clear the scene
     */
    private void clear() {

        GlUtil.checkGlError("draw start");

        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (textRect != null) {
            textRect.draw(textProgram, streamMatrix, GlUtil.IDENTITY_MATRIX);
        }

        mWindowSurface.swapBuffers();

        GlUtil.checkGlError("draw done");
    }

    private void createText(String text) {

        Bitmap bitmap = fromText(text);

        textRect = new Sprite3d(new Drawable2d(bitmap.getWidth(), bitmap.getHeight()));
        int logoTextureId = textProgram.createTextureObject();
        textRect.setTextureId(logoTextureId);
        textProgram.setBitmap(bitmap, logoTextureId);

        bitmap.recycle();

        int width = mWindowSurfaceWidth;
        int height = mWindowSurfaceHeight;

        Timber.i(width + ", " + height);

        textRect.transform(new Sprite3d.Transformer()
                .translate(width / 2f, height / 2f, 0)
                .scale(1.0f, 1.0f, 1.0f)
                .rotateAroundX(180)
                .build());
    }

    private Bitmap fromText(String text) {
        Paint paint = new Paint();
        paint.setTextSize(64);
        paint.setColor(Color.argb(255, 255, 255, 255));
        float baseline = -paint.ascent(); // ascent() is negative
        int width = (int) (paint.measureText(text) + 1.0f);
        int height = (int) (baseline + paint.descent() + 1.0f);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        Canvas canvas = new Canvas(bitmap);
//        canvas.drawColor(Color.argb(255, 255, 255, 255));
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawText(text, 0, baseline, paint);
        return bitmap;
    }

    void setTouchPosition(int x, int y) {
        // to handle touch
    }

    private final RtmpDataSourceFactory rtmpDataSourceFactory = new RtmpDataSourceFactory();

    private ArrayList<Sprite2d> rects = new ArrayList<>();

    private ArrayList<SimpleExoPlayer> players = new ArrayList<>();
    // Receives the output from the streams.
    private ArrayList<SurfaceTexture> surfaceTextures = new ArrayList<>();

    private ArrayList<Stream> streams = new ArrayList<>();

    void setStreams(List<Stream> newStreams) {
        boolean updated = false;
        if (newStreams.size() > MAX_STREAMS) {
            int toRemove = newStreams.size() - MAX_STREAMS;
            for (int i = 0; i < toRemove; i++) {
                streams.remove(streams.size() - 1);
            }
        }

        if (newStreams.size() > streams.size()) {
            int toAdd = newStreams.size() - streams.size();
            for (int i = 0; i < toAdd; i++) {
                updated = true;
                ScaledDrawable2d rectDrawable = new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
                Sprite2d rect = new Sprite2d(rectDrawable);

                int textureId = mTexProgram.createTextureObject();
                SurfaceTexture surfaceTexture = new SurfaceTexture(textureId);
                rect.setTexture(textureId);

                surfaceTexture.setOnFrameAvailableListener(this);

                SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(context);

                player.setVideoSurface(new Surface(surfaceTexture));

                players.add(player);
                surfaceTextures.add(surfaceTexture);
                rects.add(rect);
                streams.add(null);
            }
        }

        if (newStreams.size() < streams.size()) {
            int toRemove = streams.size() - newStreams.size();

            for (int i = 0; i < toRemove; i++) {
                updated = true;

                players.get(players.size() - 1).stop();
                players.get(players.size() - 1).release();

                surfaceTextures.get(surfaceTextures.size() - 1).release();

                players.remove(players.size() - 1);
                surfaceTextures.remove(surfaceTextures.size() - 1);
                rects.remove(rects.size() - 1);
                streams.remove(streams.size() - 1);
            }
        }

        for (int i = 0; i < streams.size(); i++) {
            if (!newStreams.get(i).equals(streams.get(i))) {
                updated = true;
                streams.set(i, newStreams.get(i));

                Uri uri = Uri.parse(Constants.Companion.getSTREAM_ENDPOINT() + streams.get(i).getName());


                MediaSource videoSource = new ProgressiveMediaSource
                        .Factory(rtmpDataSourceFactory)
                        .createMediaSource(uri);

                players.get(i).prepare(videoSource);
                players.get(i).setPlayWhenReady(true);
            }
        }

        if (updated)
            updateGeometry();
    }
}

