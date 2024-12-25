package com.example.videoclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.videoclient.databinding.ActivityMainBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private val client = OkHttpClient()
    private var serverUrl = ""  // 改为可变
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private var isConnected = false
    private val logBuilder = StringBuilder()
    private var lastProcessTime = 0L

    companion object {
        private const val TAG = "VideoClient"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val FRAME_INTERVAL = 1000L / 15
        private const val PREF_SERVER_URL = "server_url"  // 保存服务器地址的键
        private const val DEFAULT_SERVER_URL = "http://192.168.125.160:5000/video_feed"  // 默认服务器地址
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewFinder = binding.viewFinder

        // 从SharedPreferences加载上次的服务器地址，如果没有则使用默认地址
        val prefs = getPreferences(MODE_PRIVATE)
        val savedUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        binding.serverUrlInput.setText(savedUrl)
        
        // 使用默认地址进行初始连接
        serverUrl = savedUrl
        checkServerConnection()

        // 设置连接按钮点击事件
        binding.connectButton.setOnClickListener {
            val url = binding.serverUrlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存服务器地址
            prefs.edit().putString(PREF_SERVER_URL, url).apply()
            
            // 更新服务器地址并尝试连接
            serverUrl = url
            checkServerConnection()
        }

        // 请求相机权限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能运行", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        processImage(image)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "相机绑定失败", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun checkServerConnection() {
        if (serverUrl.isEmpty()) {
            addLog("请先输入服务器地址")
            return
        }

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        addLog("正在检查服务器连接: $serverUrl")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                addLog("服务器连接失败: ${e.message}")
                updateConnectionStatus(false)
            }

            override fun onResponse(call: Call, response: Response) {
                addLog("服务器连接成功: ${response.code}")
                updateConnectionStatus(true)
                response.close()
            }
        })
    }

    private fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        runOnUiThread {
            binding.connectionStatus.text = if (connected) "已连接" else "未连接"
            binding.connectionStatus.setBackgroundColor(
                if (connected) 
                    resources.getColor(android.R.color.holo_green_dark, theme) 
                else 
                    resources.getColor(android.R.color.black, theme)
            )
        }
    }

    private fun processImage(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < FRAME_INTERVAL) {
            image.close()
            return
        }
        lastProcessTime = currentTime

        if (!isConnected) {
            addLog("未连接服务器,跳过图像处理")
            image.close()
            return
        }

        try {
            // 获取YUV格式数据
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            // 创建YuvImage
            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                image.width,
                image.height,
                null
            )

            // 压缩为JPEG
            val outputStream = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, image.width, image.height),
                85,
                outputStream
            )
            
            val jpegBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(jpegBytes, Base64.DEFAULT)
            val json = JSONObject().put("image", base64Image)
            
            val request = Request.Builder()
                .url(serverUrl)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            addLog("发送图像数据到服务器: $serverUrl")
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    addLog("发送图像数据失败: ${e.message}")
                    updateConnectionStatus(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    addLog("发送图像数据成功: ${response.code}")
                    updateConnectionStatus(true)
                    response.close()
                }
            })
        } catch (e: Exception) {
            addLog("图像处理失败: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logMessage = "[$timestamp] $message\n"
        logBuilder.append(logMessage)
        runOnUiThread {
            binding.logTextView.text = logBuilder.toString()
            binding.logScrollView.post {
                binding.logScrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
            }
        }
        Log.d(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
