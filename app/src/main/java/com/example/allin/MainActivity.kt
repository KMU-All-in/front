package com.example.allin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 시스템 바(상태바, 내비게이션바) 숨기기 처리
        hideSystemBars()

        // 2. 다른 앱 위에 표시(오버레이) 권한 점검 및 요청 🌟[추가]
        checkOverlayPermission()

        // 3. 앱 실행 시 첫 화면 설정 (기본값: HomeFragment)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        setupNavigation()
        setupBackPress()

        // 4. 백그라운드 서비스나 알림을 통해 진입했을 때의 이동 신호 처리 🌟[추가]
        handleIntent(intent)
    }

    // 이미 앱이 켜져 있는 상태에서 서비스가 다시 메인을 소환했을 때 신호를 캐치합니다. 🌟[추가]
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 새로 받은 인텐트로 교체
        handleIntent(intent)
    }

    // 서비스/알림 배달원으로부터 신호(BUDGET)를 받아 프래그먼트를 쪼개 여는 제어 함수 🌟[추가]
    private fun handleIntent(intent: Intent?) {
        val target = intent?.getStringExtra("TARGET_FRAGMENT")
        if (target == "BUDGET") {
            // 주간 계획 미작성 상태에서 쇼핑 앱이 감지되면 서비스가 이리로 보냅니다.
            replaceFragment(BudgetFragment())
        }
    }

    // 🌟 다른 앱 위에 표시 권한 확인 및 가이드 설정 [추가]
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "쇼핑 앱 차단을 위해 '다른 앱 위에 표시' 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()

                // 스마트폰의 [다른 앱 위에 표시] 설정 창으로 해당 앱 패키지명을 들고 직행합니다.
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
        // [홈 버튼] 클릭
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            replaceFragment(HomeFragment())
        }

        // [가짜 장바구니 버튼] 클릭
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            replaceFragment(FakeCartFragment())
        }

        // [예산 설정 버튼] 클릭
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
            currentFragment is HomeFragment && fragment is FakeCartFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            currentFragment is FakeCartFragment && fragment is HomeFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            currentFragment is HomeFragment && fragment is BudgetFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            currentFragment is BudgetFragment && fragment is HomeFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            currentFragment is BudgetFragment && fragment is FakeCartFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            currentFragment is FakeCartFragment && fragment is BudgetFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            else -> {
                transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}