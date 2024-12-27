package com.arthurgichuhi.a4cameras

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {
    var frontId:String=""
    var backId:String=""
    val configs = mutableListOf<OutputConfiguration>()
    val imageReader= ImageReader.newInstance(1024,720,ImageFormat.JPEG,2)
    val imageReader2= ImageReader.newInstance(1024,720,ImageFormat.JPEG,2)
    lateinit var imageBitmap: Bitmap
    lateinit var imageBitmap2:Bitmap
    private lateinit var session: CameraCaptureSession
    private lateinit var session2:CameraCaptureSession
    lateinit var req:CaptureRequest.Builder
    lateinit var req2:CaptureRequest.Builder
    lateinit var frontDevice:CameraDevice
    lateinit var backDevice:CameraDevice
    lateinit var handler:Handler
    lateinit var handlerThread:HandlerThread
    lateinit var handler2:Handler
    lateinit var handlerThread2:HandlerThread
    var matrix= Matrix().apply { postRotate(-270f) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Log.d("Image-Init","Receiving Image ${::imageBitmap.isInitialized}")
            MainPreview(if(::imageBitmap.isInitialized)imageBitmap else null,if(::imageBitmap2.isInitialized)imageBitmap2 else null)
        }
        if(!checkCameraPermission()){
            requestCameraPermission()
        }
        handlerThread= HandlerThread("front")
        handlerThread2= HandlerThread("back")
        handlerThread.start()
        handlerThread2.start()
        handler= Handler(handlerThread.looper)
        handler2=Handler(handlerThread2.looper)

        sortCameraIds(getCameraLists())
        Log.d("Image2","Cameras --------- ${getCameraLists().size}")
        openCameras()
        imageReader.setOnImageAvailableListener({reader->
            val image = reader.acquireNextImage()
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Convert bytes to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            Log.d("Image2","Front Bitmap Height - ${bitmap.height} - ${bitmap.width}")
            // Update the Compose state with the Bitmap
            imageBitmap = Bitmap.createBitmap(bitmap,0,0,image?.width?:400,image?.height?:400,matrix,false)
            runOnUiThread{
                setContent {
                    MainPreview(if(::imageBitmap.isInitialized)imageBitmap else null,if(::imageBitmap2.isInitialized)imageBitmap2 else null)
                }
            }
            Log.d("Image-Init","Receiving Image ${::imageBitmap.isInitialized}")
            image.close()
        },null)
        imageReader2.setOnImageAvailableListener({reader->
            val image = reader.acquireNextImage()
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Convert bytes to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            Log.d("Image2","Back Bitmap Height - ${bitmap.height} - ${bitmap.width}")
            // Update the Compose state with the Bitmap
            imageBitmap2 = Bitmap.createBitmap(bitmap,0,0,image?.width?:400,image?.height?:400,matrix,false)
            Log.d("Image-Init","Receiving Image ${::imageBitmap2.isInitialized}")
            image.close()
        },null)
    }

    @Composable
    fun MainPreview(bitmap: Bitmap?,bitmap2:Bitmap?) {

        Box(
            modifier = Modifier
                .fillMaxSize().background(color = Color.Black),
        ){
                if(bitmap!=null)Image(bitmap = bitmap.asImageBitmap(),
                    modifier = Modifier.align(
                    Alignment.CenterStart).fillMaxSize(.5f), contentDescription = "Bitmap")
                if (bitmap2!=null)Image(bitmap = bitmap2.asImageBitmap(),
                    modifier = Modifier.align(
                    Alignment.CenterEnd).fillMaxSize(.5f), contentDescription = "Bitmap")

        }
    }
    fun getCameraLists():List<String>{
        val cameraManager=getSystemService(CAMERA_SERVICE) as CameraManager
        return cameraManager.cameraIdList.toList()
    }

    fun sortCameraIds(cameras:List<String>){
        var cameraManager=getSystemService(CAMERA_SERVICE) as CameraManager
        for(id in cameras){
            val attributes=cameraManager.getCameraCharacteristics(id)
            if(attributes.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT){
                frontId=id
            }
            if(attributes.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK){
                backId=id
                Log.d("Image2","Got BackId")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun openCameras(){
        val cameraManager=getSystemService(CAMERA_SERVICE) as CameraManager
        val targets = mutableListOf(imageReader.surface,imageReader2.surface)
        targets.forEach{
            configs.add(OutputConfiguration(it))
        }
        if(frontId.isNotEmpty()){
            cameraManager.openCamera(frontId,object:CameraDevice.StateCallback(){
                override fun onOpened(p0: CameraDevice) {
                    frontDevice=p0
                    req=frontDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    req.addTarget(imageReader.surface)
                    frontDevice.createCaptureSession(
                        arrayOf(imageReader.surface).toList(),
                        object:CameraCaptureSession.StateCallback(){
                        override fun onConfigured(p0: CameraCaptureSession) {
                            session=p0
                            session.setRepeatingRequest(req.build(),null,null)
                        }
                        override fun onConfigureFailed(p0: CameraCaptureSession) {
                            TODO("Not yet implemented")
                        }

                    },handler)
                }
                override fun onDisconnected(p0: CameraDevice) {
                    TODO("Not yet implemented")
                }

                override fun onError(p0: CameraDevice, p1: Int) {
                    Log.d("Image","Error $p1")
                }
            },handler)
        }
        if(backId.isNotEmpty()){
            cameraManager.openCamera(backId,object:CameraDevice.StateCallback(){
                override fun onOpened(p0: CameraDevice) {
                    backDevice=p0
                    req2=backDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    req2.addTarget(imageReader2.surface)
                    backDevice.createCaptureSession(
                        arrayOf(imageReader2.surface).toList(),
                        object:CameraCaptureSession.StateCallback(){
                            override fun onConfigured(p0: CameraCaptureSession) {
                                session2=p0
                                session2.setRepeatingRequest(req2.build(),null,null)
                            }
                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                Log.d("Image2","Error $p0")
                            }

                        },handler2)
                }
                override fun onDisconnected(p0: CameraDevice) {
                    TODO("Not yet implemented")
                }

                override fun onError(p0: CameraDevice, p1: Int) {
                    Log.d("Image2","Error $p0\n$p1")
                }
            },handler2)
        }
    }

    //check for camera permission
    private fun checkCameraPermission():Boolean{
        return CAMERAX_PERMISSION.any {
            ContextCompat.checkSelfPermission(applicationContext,it)== PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestCameraPermission() {
        return ActivityCompat.requestPermissions(
            this, CAMERAX_PERMISSION, 0
        )
    }
    companion object{
        private val CAMERAX_PERMISSION= arrayOf(
            Manifest.permission.CAMERA
        )
        private val LOCATION_PERMISSION= arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private val NOTIFICATION_PERMISSION= arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

}