package com.guguma.guguma_application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkCameraPermission()

        findViewById<ImageButton>(R.id.close_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shutter_button).setOnClickListener {
            takePicture()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) startCamera() else {
                    Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraActivity", "카메라 시작 실패", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePicture() {
        if (!::imageCapture.isInitialized) return

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val originalBitmap = imageProxy.toBitmap()
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees  // 회전 정보 가져오기
                imageProxy.close()

                // Bitmap 해상도 축소 및 회전 적용
                val rotatedBitmap = originalBitmap?.let {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())  // 회전 정보 적용
                    Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
                }

                val bitmap = rotatedBitmap?.let {
                    val newWidth = (it.width * 0.5).toInt()  // 너비의 80%
                    val newHeight = (it.height * 0.5).toInt()  // 높이의 80%
                    Bitmap.createScaledBitmap(it, newWidth, newHeight, true)
                }

                bitmap?.let {
//                    runOnUiThread { showCompletedDialog(it) }
                    runOnUiThread { detectImage(it) }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraActivity", "사진 촬영 실패: ${exception.message}")
            }
        })
    }


    private fun showCompletedDialog(bitmap: Bitmap) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("촬영 완료")
            .setMessage("촬영이 완료되었습니다.\n확인 버튼을 눌러주세요.")
            .setPositiveButton("확인") { _, _ -> statusImage(bitmap) }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun detectImage(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(BuildConfig.API_PLANT_DETECT)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        runOnUiThread { showWarningDialog("서버 오류: ${res.message}") }
                        return
                    }

                    val responseBody = res.body?.string() ?: return
                    val jsonResponse = JSONObject(responseBody)
                    val message = jsonResponse.optString("message", "식별 실패")

                    runOnUiThread {
                        handleServerMessage(message, bitmap) // 서버로부터의 응답 처리
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showWarningDialog("네트워크 오류가 발생했습니다.") }
            }
        })
    }

    private fun statusImage(bitmap: Bitmap) {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userUuid = prefs.getString("userUuid", null)

        if (userUuid.isNullOrEmpty()) {
            Toast.makeText(this, "UUID를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "image.jpg", byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .addFormDataPart("userUuid", userUuid)
            .build()

        val request = Request.Builder()
            .url(BuildConfig.API_CAMERA_STATUS)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@CameraActivity, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody ?: "{}")

                    val name = jsonResponse.optString("name", "Unknown") ?: "Unknown"
                    val status = jsonResponse.optString("status", "No status available") ?: "No status available"
                    val remedy = jsonResponse.optString("remedy", "No remedy available") ?: "No remedy available"
                    val imageUrl = jsonResponse.optString("imageUrl", "") ?: ""

                    runOnUiThread {
                        moveToDetailPlantActivity(name, status, remedy, imageUrl)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "서버초코오류: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun moveToDetailPlantActivity(name: String, status: String, remedy: String, imageUrl: String) {
        val intent = Intent(this, DetailPlantActivity::class.java).apply {
            putExtra("plantName", name)
            putExtra("plantStatus", status)
            putExtra("plantRemedy", remedy)
            putExtra("plantImageUrl", imageUrl)
        }
        startActivity(intent)
        finish()
    }

    private fun handleServerMessage(message: String, bitmap: Bitmap) {
        when (message) {
            "식물을 등록하시겠습니까?" -> showCompletedDialog(bitmap)
            else -> showWarningDialog(message)
        }
    }

    private fun showWarningDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss() // 다이얼로그를 닫습니다
            }
            .setCancelable(false)
            .show()
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val buffer = planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Log.e("CameraActivity", "Bitmap 변환 실패", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessCameraProvider.getInstance(this).get().unbindAll()


    }
}