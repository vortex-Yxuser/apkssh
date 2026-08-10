package com.example.sshpayloadvpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etPayload: EditText
    private lateinit var swProxy: Switch
    private lateinit var etProxyHost: EditText
    private lateinit var etProxyPort: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvLogs: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("log_msg") ?: ""
            runOnUiThread {
                tvLogs.append("\n> $message")
                // التمرير التلقائي لأسفل السجلات
                val scrollAmount = tvLogs.layout?.getLineTop(tvLogs.lineCount)?.minus(tvLogs.height) ?: 0
                if (scrollAmount > 0) tvLogs.scrollTo(0, scrollAmount)
                
                if (message.contains("متصل بالكامل")) {
                    tvStatus.text = "متصل ✅"
                } else if (message.contains("قطع الاتصال") || message.contains("فشل")) {
                    tvStatus.text = "غير متصل"
                }
            }
        }
    }

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            tvStatus.text = "تم رفض إذن VPN"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etPayload = findViewById(R.id.etPayload)
        swProxy = findViewById(R.id.swProxy)
        etProxyHost = findViewById(R.id.etProxyHost)
        etProxyPort = findViewById(R.id.etProxyPort)
        tvStatus = findViewById(R.id.tvStatus)
        tvLogs = findViewById(R.id.tvLogs)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        tvLogs.movementMethod = ScrollingMovementMethod()

        val cfg = Prefs.load(this)
        etHost.setText(cfg.sshHost)
        etPort.setText(cfg.sshPort.toString())
        etUser.setText(cfg.sshUser)
        etPass.setText(cfg.sshPass)
        etPayload.setText(cfg.payload)
        swProxy.isChecked = cfg.proxyEnabled
        etProxyHost.setText(cfg.proxyHost)
        etProxyPort.setText(if (cfg.proxyPort != 0) cfg.proxyPort.toString() else "")

        tvStatus.text = if (MyVpnService.isRunning) "متصل ✅" else "غير متصل"

        btnConnect.setOnClickListener { onConnectClicked() }
        btnDisconnect.setOnClickListener {
            startService(Intent(this, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_DISCONNECT
            })
        }

        // تسجيل مستقبل السجلات
        val filter = IntentFilter(MyVpnService.ACTION_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }

    private fun onConnectClicked() {
        val config = TunnelConfig(
            sshHost = etHost.text.toString().trim(),
            sshPort = etPort.text.toString().trim().toIntOrNull() ?: 22,
            sshUser = etUser.text.toString().trim(),
            sshPass = etPass.text.toString(),
            payload = etPayload.text.toString(),
            proxyEnabled = swProxy.isChecked,
            proxyHost = etProxyHost.text.toString().trim(),
            proxyPort = etProxyPort.text.toString().trim().toIntOrNull() ?: 0
        )

        if (config.sshHost.isBlank() || config.sshUser.isBlank()) {
            Toast.makeText(this, "الرجاء إدخال المضيف واسم المستخدم", Toast.LENGTH_SHORT).show()
            return
        }

        Prefs.save(this, config)
        tvLogs.text = "بدء الخدمة..."

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPrepareLauncher.launch(prepareIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        tvStatus.text = "جاري الاتصال..."
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logReceiver)
    }
}
