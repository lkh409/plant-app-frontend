package com.guguma.guguma_application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import com.guguma.guguma_application.CameraActivity
import org.json.JSONObject

class CreatePlantStartActivity : AppCompatActivity() {

    private val STORAGE_PERMISSION_CODE = 1001 // 권한 코드 정의
    private val client = OkHttpClient() // OkHttpClient 정의

    // 이미지 선택 결과를 처리할 pickImageLauncher 정의
    private val pickImageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { imageUri ->
                    // 갤러리에서 선택된 이미지 URI를 서버로 업로드
                    uploadImageToServer(imageUri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_plant_start)

        // 갤러리 버튼 설정
        val bringGallery: Button = findViewById(R.id.bringGalleryBtn)
        bringGallery.setOnClickListener {
            checkPermissionAndOpenGallery()
        }

        val takePic: Button = findViewById(R.id.TakePicBtn)

        // 버튼 클릭 리스너 설정
        takePic.setOnClickListener {
            // 사진 촬영을 위한 액티비티로 이동
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        // 뒤로가기 버튼 설정
        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish() // 현재 화면 종료
        }
    }

    // 갤러리 접근 권한 체크 및 열기
    private fun checkPermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // 권한이 이미 허용된 경우
                navigatePhotos()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // 권한 거부 후 추가 설명이 필요한 경우
                showPermissionContextPopup(permission)
            }
            else -> {
                // 권한 요청 실행
                requestPermissions(arrayOf(permission), STORAGE_PERMISSION_CODE)
            }
        }
    }

    // 권한 요청 교육용 팝업
    private fun showPermissionContextPopup(permission: String) {
        AlertDialog.Builder(this)
            .setTitle("갤러리 권한 필요")
            .setMessage("갤러리에서 사진을 선택하려면 권한이 필요합니다.")
            .setPositiveButton("권한 허용") { _, _ ->
                requestPermissions(arrayOf(permission), STORAGE_PERMISSION_CODE)
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "갤러리 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .create()
            .show()
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "저장소 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
                navigatePhotos()
            } else {
                Toast.makeText(this, "저장소 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 갤러리 열기
    private fun navigatePhotos() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun uploadImageToServer(imageUri: Uri) {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userUuid = prefs.getString("userUuid", null)

        if (userUuid.isNullOrEmpty()) {
            Toast.makeText(this, "UUID를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val inputStream = contentResolver.openInputStream(imageUri) ?: return
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Bitmap 회전 및 해상도 조정
            val matrix = android.graphics.Matrix().apply {
                postRotate(90f)  // 90도 회전
            }

            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0, 0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )


            // 이미지 크기를 80%로 조정
            val resizedBitmap = Bitmap.createScaledBitmap(
                rotatedBitmap,
                (rotatedBitmap.width * 0.5).toInt(),
                (rotatedBitmap.height * 0.5).toInt(),
                true
            )

            // Bitmap을 ByteArray로 변환
            val stream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()

            // 서버로 보낼 준비
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "image.jpg", byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                .addFormDataPart("userUuid", userUuid)
                .build()

            val request = Request.Builder()
                .url(BuildConfig.API_CAMERA_STATUS) // 서버 업로드 URL
                .post(requestBody)
                .build()

            // 서버에 요청을 비동기로 보내고 응답 처리
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@CreatePlantStartActivity, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@CreatePlantStartActivity, "서버초코오류: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "이미지 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
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


    // 서버 메시지 처리 함수
    private fun handleServerMessage(message: String, responseBody: String) {
        when (message) {
            "식물을 등록하시겠습니까?" -> showRegistrationDialog(responseBody)
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

    // 등록 다이얼로그 표시
    private fun showRegistrationDialog(responseBody: String) {
        AlertDialog.Builder(this)
            .setTitle("등록 확인")
            .setMessage("서버에서 반환된 메시지: $responseBody\n등록하시겠습니까?")
            .setPositiveButton("등록") { _, _ ->
                Toast.makeText(this, "식물이 등록되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}