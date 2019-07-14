package com.winterparadox.nginxplayer.monitorwall


import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.winterparadox.nginxplayer.common.Constants.Companion.SERVER
import com.winterparadox.nginxplayer.databinding.FragmentMonitorBinding
import com.winterparadox.nginxplayer.di.MonitorViewModelFactory
import timber.log.Timber


class MonitorFragment : Fragment(), SurfaceViewParent, SurfaceHolder.Callback {


    private lateinit var mainHandler: MainHandler
    private var surfaceHolder: SurfaceHolder? = null
    private var renderThread: RenderThread? = null

    private lateinit var viewModel: MonitorViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val binding = FragmentMonitorBinding.inflate(inflater, container, false)

        val surfaceView = binding.surfaceView

        // initialise the render stuff

        mainHandler = MainHandler(this)

        val sh = surfaceView.holder
        sh.addCallback(this)

        viewModel = ViewModelProviders.of(this, MonitorViewModelFactory(build(SERVER)))
            .get(MonitorViewModel::class.java)

        return binding.root
    }


    override fun onResume() {
        Timber.d("onResume BEGIN")
        super.onResume()

        renderThread = RenderThread(activity, mainHandler)
        renderThread?.name = "TexFromCam Render"
        renderThread?.start()
        renderThread?.waitUntilReady()

        val rh = renderThread?.handler

        if (surfaceHolder == null) {
            Timber.d("No previous surface")
        } else {
            Timber.d("Sending previous surface")
            rh?.sendSurfaceAvailable(surfaceHolder, false)
        }
        Timber.d("onResume END")
    }

    override fun onPause() {
        Timber.d("onPause BEGIN")
        super.onPause()

        val rh = renderThread?.handler
        rh?.sendShutdown()
        try {
            renderThread?.join()
        } catch (ie: InterruptedException) {
            // not expected
            throw RuntimeException("join was interrupted", ie)
        }

        renderThread = null
        Timber.d("onPause END")
    }


    // ____ SurfaceViewParent Methods ____

    override fun showPreviewSize(x: Int, y: Int) {
//        Toast.makeText(activity, "$x, $y", Toast.LENGTH_LONG).show()
    }

    override fun onDisplayReady() {
        // observing streams
        viewModel.streams.observe(this, Observer {
            it?.let {
                val rh = renderThread?.handler
                rh?.submitStreams (it)
            }
        })
    }

    // ____ SurfaceHolder Callback ____

    override// SurfaceHolder.Callback
    fun surfaceCreated(holder: SurfaceHolder) {
        Timber.d("surfaceCreated holder=$holder (static=$surfaceHolder)")
        if (surfaceHolder != null) {
            throw RuntimeException("surfaceHolder is already set")
        }

        surfaceHolder = holder

        if (renderThread == null) {
            // Sometimes see this on 4.4.x N5: power off, power on, unlock, with device in
            // landscape and a lock screen that requires portrait.  The surface-created
            // message is showing up after onPause().
            //
            // Chances are good that the surface will be destroyed before the activity is
            // unpaused, but we track it anyway.  If the activity is un-paused and we start
            // the RenderThread, the SurfaceHolder will be passed in right after the thread
            // is created.
            Timber.d("render thread not running")
        } else {
            // Normal case -- render thread is running, tell it about the new surface.
            val rh = renderThread?.handler
            rh?.sendSurfaceAvailable(holder, true)
        }
    }

    override// SurfaceHolder.Callback
    fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Timber.d("surfaceChanged fmt=$format size=${width}x$height holder=$holder")

        if (renderThread == null) {
            Timber.d("Ignoring surfaceChanged")
            return
        }
        val rh = renderThread?.handler
        rh?.sendSurfaceChanged(format, width, height)
    }

    override// SurfaceHolder.Callback
    fun surfaceDestroyed(holder: SurfaceHolder) {
        // In theory we should tell the RenderThread that the surface has been destroyed.
        if (renderThread == null) {
            Timber.d("surfaceDestroyed holder=$holder")
            surfaceHolder = null
            return
        }
        val rh = renderThread?.handler
        rh?.sendSurfaceDestroyed()
    }

}
