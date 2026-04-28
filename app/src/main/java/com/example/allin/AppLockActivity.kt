package com.example.allin

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allin.worker.AppMonitorService
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AppLockActivity : AppCompatActivity() {

    private lateinit var swMainLock: SwitchCompat
    private lateinit var rvLockedApps: RecyclerView
    private lateinit var btnAddApp: FloatingActionButton
    private lateinit var btnMoreOptions: ImageButton
    private lateinit var btnChangePassword: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)

        initViews()
        setupListeners()
        setupRecyclerView()
        
        // 권한 확인 및 요청
        checkPermissions()

        // E1: 비밀번호 오류 알림을 통해 들어온 경우 즉시 변경 다이얼로그 표시
        if (intent.getBooleanExtra("EXTRA_CHANGE_PASSWORD", false)) {
            showPasswordChangeDialog()
        }
    }

    private fun initViews() {
        swMainLock = findViewById(R.id.swMainLock)
        rvLockedApps = findViewById(R.id.rvLockedApps)
        btnAddApp = findViewById(R.id.btnAddApp)
        btnMoreOptions = findViewById(R.id.btnMoreOptions)
        btnChangePassword = findViewById(R.id.btnChangePassword)
    }

    private fun setupListeners() {
        // A1. 쇼핑 앱 잠금 기능 on/off
        swMainLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasUsageStatsPermission()) {
                    startLockService()
                    Toast.makeText(this, "잠금 서비스가 시작되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    swMainLock.isChecked = false
                    requestUsageStatsPermission()
                }
            } else {
                stopLockService()
                Toast.makeText(this, "잠금 서비스가 종료되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        btnAddApp.setOnClickListener {
            val intent = Intent(this, AppSelectActivity::class.java)
            startActivity(intent)
        }

        btnMoreOptions.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("잠금 리스트 전체 삭제")
            popup.setOnMenuItemClickListener {
                showDeleteAllConfirmDialog()
                true
            }
            popup.show()
        }

        btnChangePassword.setOnClickListener {
            showPasswordChangeDialog()
        }
    }

    private fun startLockService() {
        val intent = Intent(this, AppMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLockService() {
        stopService(Intent(this, AppMonitorService::class.java))
    }

    private fun checkPermissions() {
        if (!hasUsageStatsPermission()) {
            AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage("앱 잠금 기능을 위해 '사용 정보 접근 권한'이 필요합니다. 설정 화면으로 이동하시겠습니까?")
                .setPositiveButton("이동") { _, _ -> requestUsageStatsPermission() }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun setupRecyclerView() {
        rvLockedApps.layoutManager = LinearLayoutManager(this)
    }

    private fun showDeleteAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("전체 삭제")
            .setMessage("쇼핑 앱 잠금 리스트를 삭제하겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                Toast.makeText(this, "리스트가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showPasswordChangeDialog() {
        // S4: 비밀번호 변경 시나리오 구현
        val builder = AlertDialog.Builder(this)
        builder.setTitle("비밀번호 변경")
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val etOldPass = EditText(this).apply { hint = "기존 비밀번호 입력" }
        val etNewPass = EditText(this).apply { hint = "새 비밀번호 입력" }
        layout.addView(etOldPass)
        layout.addView(etNewPass)
        
        builder.setView(layout)
        builder.setPositiveButton("변경") { _, _ ->
            // 여기에 실제 저장 로직 추가 가능
            Toast.makeText(this, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }
}
