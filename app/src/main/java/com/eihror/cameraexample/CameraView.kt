package com.eihror.cameraexample

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayList
import java.util.Arrays

class CameraView : TextureView {

  private var cameraId: String? = null
  var cameraDevice: CameraDevice? = null
  private var cameraCaptureSessions: CameraCaptureSession? = null
  private var captureRequest: CaptureRequest? = null
  private var captureRequestBuilder: CaptureRequest.Builder? = null
  private var imageDimension: Size? = null
  private var imageReader: ImageReader? = null
  private val file: File? = null
  private val mFlashSupported: Boolean = false
  var mBackgroundHandler: Handler? = null
  private var mBackgroundThread: HandlerThread? = null

  private var textureListener: SurfaceTextureListener =
    object : SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int
      ) {
        //open your camera here
        openCamera()
      }

      override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int
      ) {
        // Transform you image captured size according to the surface width and height
      }

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return false
      }

      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
  private val stateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
      //This is called when the camera is open
      Log.e(TAG, "onOpened")
      cameraDevice = camera
      createCameraPreview()
    }

    override fun onDisconnected(camera: CameraDevice) {
      cameraDevice!!.close()
    }

    override fun onError(
      camera: CameraDevice,
      error: Int
    ) {
      cameraDevice!!.close()
      cameraDevice = null
    }
  }
  internal val captureCallbackListener: CameraCaptureSession.CaptureCallback =
    object : CameraCaptureSession.CaptureCallback() {
      override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
      ) {
        super.onCaptureCompleted(session, request, result)
        Toast.makeText(context, "Saved:" + file!!, Toast.LENGTH_SHORT)
            .show()
        createCameraPreview()
      }
    }

  constructor(context: Context?) : super(context)
  constructor(
    context: Context?,
    attrs: AttributeSet?
  ) : super(context, attrs)

  constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int
  ) : super(context, attrs, defStyleAttr)

  private fun startBackgroundThread() {
    mBackgroundThread = HandlerThread("Camera Background")
    mBackgroundThread!!.start()
    mBackgroundHandler = Handler(mBackgroundThread!!.looper)
  }

  private fun stopBackgroundThread() {
    mBackgroundThread!!.quitSafely()
    try {
      mBackgroundThread!!.join()
      mBackgroundThread = null
      mBackgroundHandler = null
    } catch (e: InterruptedException) {
      e.printStackTrace()
    }

  }

  fun onResume() {
    startBackgroundThread()
    if (this@CameraView.isAvailable) {
      openCamera()
    } else {
      this@CameraView.surfaceTextureListener = textureListener
    }
  }

  fun onPause(){
    Log.e(TAG, "onPause")
    closeCamera()
    stopBackgroundThread()
  }

  inline fun captureImage(crossinline imageCaptured: (imageByte: ByteArray) -> Unit) {
    if (null == cameraDevice) {
      Log.e(TAG, "cameraDevice is null")
      return
    }

    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      val characteristics = manager.getCameraCharacteristics(cameraDevice!!.id)
      var jpegSizes: Array<Size>? = null
      if (characteristics != null) {
        jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.JPEG)
      }
      var width = this@CameraView.let { it.width }
      var height = this@CameraView.let { it.height }
      if (jpegSizes != null && jpegSizes.isNotEmpty()) {
        width = jpegSizes[0].width
        height = jpegSizes[0].height
      }
      val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
      val outputSurfaces = ArrayList<Surface>(2)
      outputSurfaces.add(reader.surface)
      outputSurfaces.add(Surface(this@CameraView.surfaceTexture))
      val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
      captureBuilder.addTarget(reader.surface)
      captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
      // Orientation
      val rotation = (context as Activity).windowManager.defaultDisplay.rotation
      captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))
      val file = File(Environment.getExternalStorageDirectory().toString() + "/pic.jpg")
      val readerListener = object : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
          var image: Image? = null
          try {
            image = reader.acquireLatestImage()
            val buffer = image!!.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            save(bytes)
            imageCaptured(bytes)
          } catch (e: FileNotFoundException) {
            e.printStackTrace()
          } catch (e: IOException) {
            e.printStackTrace()
          } finally {
            image?.close()
          }
        }

        @Throws(IOException::class)
        private fun save(bytes: ByteArray) {
          var output: OutputStream? = null
          try {
            output = FileOutputStream(file)
            output.write(bytes)
          } finally {
            output?.close()
          }
        }
      }
      reader.setOnImageAvailableListener(readerListener, mBackgroundHandler)
      val captureListener = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
          session: CameraCaptureSession,
          request: CaptureRequest,
          result: TotalCaptureResult
        ) {
          super.onCaptureCompleted(session, request, result)
          Toast.makeText(context, "Saved:$file", Toast.LENGTH_SHORT)
              .show()
          createCameraPreview()
        }
      }
      cameraDevice!!.createCaptureSession(
          outputSurfaces, object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
          try {
            session.capture(captureBuilder.build(), captureListener, mBackgroundHandler)
          } catch (e: CameraAccessException) {
            e.printStackTrace()
          }

        }

        override fun onConfigureFailed(session: CameraCaptureSession) {}
      }, mBackgroundHandler
      )
    } catch (e: CameraAccessException) {
      e.printStackTrace()
    }

  }

  fun createCameraPreview() {
    try {
      val texture = this@CameraView.surfaceTexture!!
      texture.setDefaultBufferSize(imageDimension!!.width, imageDimension!!.height)
      val surface = Surface(texture)
      captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      captureRequestBuilder?.addTarget(surface)
      cameraDevice!!.createCaptureSession(
          Arrays.asList(surface),
          object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
              //The camera is already closed
              if (null == cameraDevice) {
                return
              }
              // When the session is ready, we start displaying the preview.
              cameraCaptureSessions = cameraCaptureSession
              updatePreview()
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
              Toast.makeText(context, "Configuration change", Toast.LENGTH_SHORT)
                  .show()
            }
          }, null
      )
    } catch (e: CameraAccessException) {
      e.printStackTrace()
    }

  }

  private fun openCamera() {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    Log.e(TAG, "is camera open")
    try {
      cameraId = manager.cameraIdList[1]
      val characteristics = manager.getCameraCharacteristics(cameraId!!)
      val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
      imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
      // Add permission for camera and let user grant the permission
      if (ActivityCompat.checkSelfPermission(
              context, Manifest.permission.CAMERA
          ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
              context, Manifest.permission.WRITE_EXTERNAL_STORAGE
          ) != PackageManager.PERMISSION_GRANTED
      ) {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_CAMERA_PERMISSION
        )
        return
      }
      manager.openCamera(cameraId!!, stateCallback, null)
    } catch (e: CameraAccessException) {
      e.printStackTrace()
    }

    Log.e(TAG, "openCamera X")
  }

  private fun updatePreview() {
    if (null == cameraDevice) {
      Log.e(TAG, "updatePreview error, return")
    }
    captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getRange())

    /**
     * This may probably works
     */
    /*captureRequestBuilder?.set(
        CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT
    )
    captureRequestBuilder?.set(
        CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
        CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT
    )*/
    /**
     * This may probably works
     */

    try {
      cameraCaptureSessions?.setRepeatingRequest(
          captureRequestBuilder?.build()!!, null,
          mBackgroundHandler
      )
    } catch (e: CameraAccessException) {
      e.printStackTrace()
    }

  }

  private fun getRange(): Range<Int>? {
    var chars: CameraCharacteristics?

    try {
      chars = (context.getSystemService(
          Context.CAMERA_SERVICE
      ) as CameraManager).getCameraCharacteristics(cameraId)
      val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
      var result: Range<Int>? = null
      for (range in ranges) {
        val upper = range.upper
        // 10 - min range upper for my needs
        if (upper >= 30) {
          if (result == null || upper < result.upper.toInt()) {
            result = range
          }
        }
      }
      if (result == null) {
        result = ranges[0]
      }
      return result
    } catch (e: CameraAccessException) {
      e.printStackTrace()
      return null
    }

  }

  private fun closeCamera() {
    if (null != cameraDevice) {
      cameraDevice!!.close()
      cameraDevice = null
    }
    if (null != imageReader) {
      imageReader!!.close()
      imageReader = null
    }
  }

  companion object {
    val TAG = CameraView::class.java.name
    val ORIENTATIONS = SparseIntArray()

    init {
      ORIENTATIONS.append(Surface.ROTATION_0, 90)
      ORIENTATIONS.append(Surface.ROTATION_90, 0)
      ORIENTATIONS.append(Surface.ROTATION_180, 270)
      ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    val REQUEST_CAMERA_PERMISSION = 200
  }

}