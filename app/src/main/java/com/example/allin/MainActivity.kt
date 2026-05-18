package com.example.allin

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fakeCartRepository: FakeCartRepository
    private var isExpiredCheckDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fakeCartRepository = FakeCartRepository()

        hideSystemBars()
        checkOverlayPermission()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        setupNavigation()
        setupBackPress()
        handleIntent(intent)

        // [핵심] 앱 시작 시 만료 상품 즉시 체크
        checkFakeCartExpirations()
    }

    private fun checkFakeCartExpirations() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        lifecycleScope.launch {
            try {
                val products = fakeCartRepository.allProducts.first()
                val nowCal = Calendar.getInstance()
                
                val expiredProducts = products.filter { 
                    val expiryCal = Calendar.getInstance().apply {
                        timeInMillis = it.addedTime
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        add(Calendar.DAY_OF_YEAR, it.expiryDays)
                    }
                    nowCal.timeInMillis >= expiryCal.timeInMillis
                }

                if (expiredProducts.isNotEmpty() && !isExpiredCheckDone) {
                    showExpiredDialog(expiredProducts)
                    isExpiredCheckDone = true
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Expired check failed", e)
            }
        }
    }

    private fun showExpiredDialog(expiredProducts: List<FakeProduct>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("숙고 기간 만료 안내")
            .setMessage("날짜가 지난 상품이 ${expiredProducts.size}건 있습니다. 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    expiredProducts.forEach { fakeCartRepository.delete(it) }
                    Toast.makeText(this@MainActivity, "만료된 상품이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("유지", null)
            .create()

        dialog.setOnShowListener {
            // 버튼 글자색을 진한 회색(#212121)으로 변경하여 가독성 높임
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#212121"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#212121"))
        }
        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val target = intent?.getStringExtra("TARGET_FRAGMENT")
        if (target == "BUDGET") {
            replaceFragment(BudgetFragment())
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "쇼핑 앱 차단을 위해 '다른 앱 위에 표시' 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1001)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            replaceFragment(HomeFragment())
        }
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            replaceFragment(FakeCartFragment())
        }
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener {
            replaceFragment(BudgetFragment())
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is HomeFragment) {
                    finish()
                } else {
                    replaceFragment(HomeFragment())
                }
            }
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when {
            currentFragment is HomeFragment && fragment is FakeCartFragment -> transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            currentFragment is FakeCartFragment && fragment is HomeFragment -> transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            currentFragment is HomeFragment && fragment is BudgetFragment -> transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            currentFragment is BudgetFragment && fragment is HomeFragment -> transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            currentFragment is BudgetFragment && fragment is FakeCartFragment -> transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            currentFragment is FakeCartFragment && fragment is BudgetFragment -> transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            else -> transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}
