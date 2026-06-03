package com.example.allin

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object LockPasswordDialog {

    fun show(activity: AppCompatActivity) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val et = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "숫자 4자리"
            setSingleLine(true)
        }

        AlertDialog.Builder(activity)
            .setTitle("새 비밀번호 설정")
            .setView(et)
            .setPositiveButton("변경") { _, _ ->
                val pin = et.text.toString()

                if (pin.length == 4) {
                    activity.getSharedPreferences("LockPrefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("LOCK_PIN", pin)
                        .apply()

                    db.collection("users").document(currentUser.uid)
                        .set(mapOf("lock_pin" to pin), SetOptions.merge())
                        .addOnSuccessListener {
                            Toast.makeText(activity, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(activity, "서버 저장에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(activity, "4자리 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}