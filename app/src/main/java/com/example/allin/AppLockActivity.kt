package com.example.allin

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allin.worker.AppMonitorService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppLockActivity : AppCompatActivity() {

    private lateinit var swMainLock: SwitchCompat
    private lateinit var rvLockedApps: RecyclerView
    private lateinit var btnAddApp: FloatingActionButton
    private lateinit var btnMoreOptions: ImageButton
    private lateinit var btnChangePassword: LinearLayout
    
    private lateinit var adapter: LockedAppAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)

        initViews()
        setupRecyclerView()
        setupListeners()
        
        // 1. 권한 확인 (사용 정보 + 알림)
        checkAndRequestPermissions()
        
        observeLockedApps()
        swMainLock.isChecked = isServiceRunning(AppMonitorService::class.java)
    }

    private fun initViews() {
        swMainLock = findViewById(R.id.swMainLock)
        rvLockedApps = findViewById(R.id.rvLockedApps)
        btnAddApp = findViewById(R.id.btnAddApp)
        btnMoreOptions = findViewById(R.id.btnMoreOptions)
        btnChangePassword = findViewById(R.id.btnChangePassword)
    }

    private fun setupRecyclerView() {
        adapter = LockedAppAdapter(emptyList()) { packageName, isLocked ->
            if (!isLocked) removeLockedApp(packageName)
        }
        rvLockedApps.layoutManager = LinearLayoutManager(this)
        rvLockedApps.adapter = adapter
    }

    private fun setupListeners() {
        swMainLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasUsageStatsPermission()) {
                    startLockService()
                } else {
                    swMainLock.isChecked = false
                    requestUsageStatsPermission()
                }
            } else {
                stopLockService()
            }
        }

        btnAddApp.setOnClickListener {
            startActivity(Intent(this, AppSelectActivity::class.java))
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

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
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

    private fun checkAndRequestPermissions() {
        // 1. 사용 정보 접근 권한 확인
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        }
        
        // 2. 알림 권한 확인 (안드로이드 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
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
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("앱 잠금을 위해 '사용 정보 접근 권한'이 필요합니다. 설정으로 이동하시겠습니까?")
            .setPositiveButton("이동") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun observeLockedApps() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val packageNames = snapshot.get("locked_apps") as? List<String> ?: emptyList()
                    loadAppDetails(packageNames)
                }
            }
    }

    private fun loadAppDetails(packageNames: List<String>) {
        val pm = packageManager
        val lockedApps = packageNames.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                LockedApp(pkg, appInfo.loadLabel(pm).toString(), appInfo.loadIcon(pm))
            } catch (e: Exception) { null }
        }
        adapter.updateData(lockedApps)
    }

    private fun removeLockedApp(packageName: String) {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).get().addOnSuccessListener { snapshot ->
            val apps = snapshot.get("locked_apps") as? MutableList<String> ?: mutableListOf()
            apps.remove(packageName)
            db.collection("users").document(currentUser.uid).update("locked_apps", apps)
        }
    }

    private fun showDeleteAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("전체 삭제")
            .setMessage("잠금 리스트를 모두 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                val user = auth.currentUser ?: return@setPositiveButton
                db.collection("users").document(user.uid).update("locked_apps", emptyList<String>())
            }
            .show()
    }

    private fun showPasswordChangeDialog() {
        val user = auth.currentUser ?: return
        val et = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("새 비밀번호").setView(et)
            .setPositiveButton("변경") { _, _ ->
                val pin = et.text.toString()
                if (pin.length == 4) db.collection("users").document(user.uid).update("lock_pin", pin)
            }.show()
    }
}
