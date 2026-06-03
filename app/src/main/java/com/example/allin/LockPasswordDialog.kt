package com.example.allin

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.Button
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

        val dialogView = activity.layoutInflater.inflate(
            R.layout.dialog_change_lock_password,
            null
        )

        val et = dialogView.findViewById<EditText>(R.id.etLockPin)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnChange = dialogView.findViewById<Button>(R.id.btnChange)

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val width = (activity.resources.displayMetrics.widthPixels * 0.86).toInt()
            dialog.window?.setLayout(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnChange.setOnClickListener {
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
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            activity,
                            "서버 저장에 실패했습니다: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } else {
                Toast.makeText(activity, "4자리 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}