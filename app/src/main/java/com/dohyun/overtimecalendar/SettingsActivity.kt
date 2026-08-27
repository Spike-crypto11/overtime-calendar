package com.dohyun.overtimecalendar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val tvResult = findViewById<TextView>(R.id.tvTestResult)
        etUrl.setText(Prefs.getUrl(this))

        findViewById<Button>(R.id.btnSaveUrl).setOnClickListener {
            Prefs.setUrl(this, etUrl.text.toString())
            Toast.makeText(this, "URL 저장됨", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) { tvResult.text = "URL을 먼저 입력하세요."; return@setOnClickListener }
            tvResult.text = "연결 확인 중..."
            SyncManager.test(this, url) { ok, msg ->
                tvResult.text = msg
                tvResult.setTextColor(if (ok) 0xFF1A6E1A.toInt() else 0xFFCC0000.toInt())
                if (ok) Prefs.setUrl(this, url)
            }
        }

        findViewById<Button>(R.id.btnManageCats).setOnClickListener {
            startActivity(Intent(this, CategoryActivity::class.java))
        }
    }
}
