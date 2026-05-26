package com.example.allin

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allin.data.AppDatabase
import com.example.allin.data.LockedAppRepository
import com.example.allin.worker.AppMonitorService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class AppLockActivity : AppCompatActivity() {

    private lateinit var swMainLock: SwitchCompat
    private lateinit var rvLockedApps: RecyclerView
    private lateinit var btnAddApp: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnMoreOptions: ImageButton
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var tvLockStatus: TextView

    private lateinit var adapter: LockedAppAdapter
    private lateinit var repository: LockedAppRepository
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)
        hideSystemBars()

        val dao = AppDatabase.getDatabase(applicationContext).lockedAppDao()
        repository = LockedAppRepository(dao)

        initViews()
        setupRecyclerView()
        setupListeners()
        checkPermissions()
        observeLockedApps()

        repository.syncFromFirestore(packageManager)
        swMainLock.isChecked = isServiceRunning(AppMonitorService::class.java)
        updateLockStatusText(swMainLock.isChecked)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun initViews() {
        swMainLock = findViewById(R.id.swMainLock)
        rvLockedApps = findViewById(R.id.rvLockedApps)
        btnAddApp = findViewById(R.id.btnAddApp)
        btnBack = findViewById(R.id.btnBack)
        btnMoreOptions = findViewById(R.id.btnMoreOptions)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        tvLockStatus = findViewById(R.id.tvLockStatus)
    }

    private fun setupRecyclerView() {
        adapter = LockedAppAdapter(
            emptyList(),
            onToggle = { packageName, isActive ->
                lifecycleScope.launch {
                    repository.updateLockedAppStatus(packageName, isActive)
                }
            },
            onMoreClick = { view, app ->
                showLockedAppOptions(view, app)
            }
        )

        rvLockedApps.layoutManager = LinearLayoutManager(this)
        rvLockedApps.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        swMainLock.setOnCheckedChangeListener { _, isChecked ->
            updateLockStatusText(isChecked)

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

    private fun updateLockStatusText(isLocked: Boolean) {
        if (isLocked) {
            tvLockStatus.text = "잠금 기능 사용 중"
            tvLockStatus.setTextColor(Color.parseColor("#4358F6"))
        } else {
            tvLockStatus.text = "잠금 기능 해제"
            tvLockStatus.setTextColor(Color.parseColor("#4C4C4C"))
        }
    }

    private fun observeLockedApps() {
        lifecycleScope.launch {
            repository.allLockedApps.collect { dbApps ->
                val pm = packageManager
                val uiModels = dbApps.mapNotNull { dbApp ->
                    try {
                        val appInfo = pm.getApplicationInfo(dbApp.packageName, 0)
                        LockedApp(
                            packageName = dbApp.packageName,
                            name = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                            isActive = dbApp.isActive
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val oldApps = adapter.apps
                adapter.apps = uiModels

                if (oldApps.size != uiModels.size) {
                    adapter.notifyDataSetChanged()
                } else {
                    for (i in uiModels.indices) {
                        if (oldApps[i].isActive != uiModels[i].isActive) {
                            adapter.notifyItemChanged(i)
                        }
                    }
                }
            }
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

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun checkPermissions() {
        if (!hasUsageStatsPermission()) {
            AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage("앱 잠금을 위해 '사용 정보 접근 권한'이 필요합니다.")
                .setPositiveButton("이동") { _, _ -> requestUsageStatsPermission() }
                .show()
        }
    }

    private fun showDeleteAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("전체 삭제")
            .setMessage("쇼핑 앱 잠금 리스트를 모두 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    repository.updateAllLockedApps(emptyList())
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showPasswordChangeDialog() {
        val currentUser = auth.currentUser ?: return

        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "숫자 4자리"
        }

        AlertDialog.Builder(this)
            .setTitle("새 비밀번호 설정")
            .setView(et)
            .setPositiveButton("변경") { _, _ ->
                val pin = et.text.toString()

                if (pin.length == 4) {
                    val sharedPref = getSharedPreferences("LockPrefs", Context.MODE_PRIVATE)
                    sharedPref.edit().putString("LOCK_PIN", pin).apply()

                    db.collection("users").document(currentUser.uid)
                        .update("lock_pin", pin)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "비밀번호가 변경되었습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        this,
                        "4자리 숫자를 입력해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showLockedAppOptions(anchor: android.view.View, app: LockedApp) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("삭제")

        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.title == "삭제") {
                showRemoveLockedAppDialog(app)
            }
            true
        }

        popup.show()
    }

    private fun showRemoveLockedAppDialog(app: LockedApp) {
        AlertDialog.Builder(this)
            .setTitle("앱 삭제")
            .setMessage("${app.name} 앱을 잠금 리스트에서 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    repository.removeLockedApp(app.packageName)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}