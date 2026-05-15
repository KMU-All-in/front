package com.example.allin

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 시스템 바(상태바, 내비게이션바) 숨기기 처리
        hideSystemBars()

        // 2. 앱 실행 시 첫 화면 설정 (HomeFragment)
        if (savedInstanceState == null) {
            // 처음 앱 켤 때는 애니메이션 없이 즉시 홈 프래그먼트를 띄웁니다.
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        setupNavigation()
        setupBackPress()
    }

    // 포커스가 돌아올 때마다(예: 설정창 갔다 올 때) 다시 숨기기
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

        // [예산 설정 버튼] 클릭 (프래그먼트 완성 후 주석 해제)
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener {
            replaceFragment(BudgetFragment())
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 현재 어떤 프래그먼트가 떠 있는지 확인
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

                if (currentFragment is HomeFragment) {
                    // 1. 현재 홈 화면이면 -> 앱 종료
                    finish()
                } else {
                    // 2. 예산설정이나 장바구니 화면이면 -> 홈 화면으로 이동
                    replaceFragment(HomeFragment())

                    // (선택사항) 하단 탭 아이콘의 선택 상태도 홈으로 바꿔줘야 시각적으로 일치합니다.
                    // updateBottomNavSelection(R.id.navHome) // 만약 이런 함수가 있다면 호출
                }
            }
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        // --- 지능형 애니메이션 (슬라이드 방향 완벽 설정) ---
        when {
            // 1. [홈]에서 [장바구니]로 갈 때 (오른쪽으로 이동 -> 왼쪽으로 밀기)
            currentFragment is HomeFragment && fragment is FakeCartFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            // 2. [장바구니]에서 [홈]으로 올 때 (왼쪽으로 이동 -> 오른쪽으로 밀기)
            currentFragment is FakeCartFragment && fragment is HomeFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            // 3. [홈]에서 [예산설정]으로 갈 때 (왼쪽으로 이동 -> 오른쪽으로 밀기)
            currentFragment is HomeFragment && fragment is BudgetFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            // 4. [예산설정]에서 [홈]으로 올 때 (오른쪽으로 이동 -> 왼쪽으로 밀기)
            currentFragment is BudgetFragment && fragment is HomeFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            // 5. [예산설정]에서 바로 [장바구니]로 갈 때 (오른쪽으로 이동)
            currentFragment is BudgetFragment && fragment is FakeCartFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            // 6. [장바구니]에서 바로 [예산설정]으로 갈 때 (왼쪽으로 이동)
            currentFragment is FakeCartFragment && fragment is BudgetFragment -> {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            // 그 외 기본값
            else -> {
                transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}