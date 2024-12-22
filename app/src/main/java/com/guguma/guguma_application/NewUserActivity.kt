package com.guguma.guguma_application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class NewUserActivity : AppCompatActivity() {

    private lateinit var checkCamera: CheckBox
    private lateinit var checkGallery: CheckBox
    private lateinit var ButtonGotoMain: Button

    private val CAMERA_PERMISSION = Manifest.permission.CAMERA
    private val GALLERY_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_user_start)

        checkCamera = findViewById(R.id.checkCamera)
        checkGallery = findViewById(R.id.checkGallery)
        ButtonGotoMain = findViewById(R.id.ButtonGotoMain)

        checkCamera.setOnClickListener { checkAndRequestPermission(CAMERA_PERMISSION, checkCamera) }
        checkGallery.setOnClickListener { checkAndRequestPermission(GALLERY_PERMISSION, checkGallery) }

        // 시작 버튼 상태 확인
        updateStartButtonState()
    }

    // 권한 요청 및 체크박스 상태 업데이트
    private fun checkAndRequestPermission(permission: String, checkBox: CheckBox) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
//            Toast.makeText(this, "권한이 이미 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
        updateStartButtonState()
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                    when (permission) {
                        CAMERA_PERMISSION -> checkCamera.isChecked = true
                        GALLERY_PERMISSION -> checkGallery.isChecked = true
                    }
                } else {
                    Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateStartButtonState()
    }

    // 시작 버튼 활성화 여부 업데이트
    private fun updateStartButtonState() {
        ButtonGotoMain.isEnabled = checkCamera.isChecked && checkGallery.isChecked
        ButtonGotoMain.setOnClickListener {
            if (ButtonGotoMain.isEnabled) {
                startActivity(Intent(this, MainActivity::class.java))
            }
        }
    }
}