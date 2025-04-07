package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.PendingIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.example.myapplication.ui.theme.MyApplicationTheme
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.provider.Settings
import android.net.Uri

private const val PERMISSION_REQUEST_CODE = 1001

class MainActivity : ComponentActivity() {
    private lateinit var statusReceiver: BroadcastReceiver
    private var isEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                AppContent(
                    isEnabled = isEnabled,
                    onStateChanged = { newState -> isEnabled = newState },
                    context = this
                )
            }
        }

        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val isRunning = intent?.getBooleanExtra("status", false) ?: false
                updateUI(isRunning)
            }
        }

        registerReceiver(statusReceiver, IntentFilter("com.example.myapplication.ANTIVIRUS_STATUS"),
            Context.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusReceiver)
    }

    private fun updateUI(isRunning: Boolean) {
        isEnabled = isRunning
    }
}

@Composable
fun AppContent(
    isEnabled: Boolean,
    onStateChanged: (Boolean) -> Unit,
    context: Context
) {
    val requestOverlayPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Обработка результата */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)) {
            requestOverlayPermission.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }
    }



    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toggleService(context, !isEnabled, onStateChanged)
        } else {
            Toast.makeText(
                context,
                "Без разрешения уведомления работать не будут",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Остальной код AppContent остается без изменений
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter) // Для верхнего выравнивания текста
        ) {
            Text(
                text = "Обнаружитель APK",
                style = TextStyle(fontSize = 30.sp),
                modifier = Modifier
                    .padding(top = 50.dp)
            )
        }

        // Состояние по центру
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center, // Центрируем по вертикали
            modifier = Modifier.align(Alignment.Center) // Центрируем по всему контейнеру
        ) {
            Text(
                text = "Состояние: ${if (isEnabled) "Включено" else "Выключено"}",
                fontSize = 20.sp,
                modifier = Modifier
            )
        }

        // Кнопка снизу
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter) // Выравнивание по центру снизу
                .padding(bottom = 50.dp) // Отступ снизу
        ) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            toggleService(context, !isEnabled) { newState ->
                                onStateChanged(newState)
                            }
                        } else {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        toggleService(context, !isEnabled) { newState ->
                            onStateChanged(newState)
                        }
                    }
                },
                modifier = Modifier
                    .size(200.dp, 60.dp)
                    .border(2.dp, Color.Black, RoundedCornerShape(30.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(173, 216, 255)
                )
            ) {
                Text(
                    text = "Изменить состояние",
                    fontSize = 15.sp,
                    color = Color.Black,
                )
            }
        }
    }
}

private fun toggleService(context: Context, enable: Boolean, onStateChanged: (Boolean) -> Unit) {
    if (enable) {
        startAntivirusService(context)
    } else {
        stopAntivirusService(context)
        showStoppedNotification(context)
    }
    onStateChanged(enable)
}

private fun startAntivirusService(context: Context) {
    try {
        val intent = Intent(context, AntivirusService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } catch (e: SecurityException) {
        Toast.makeText(
            context,
            "Ошибка: недостаточно разрешений для запуска сервиса",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun stopAntivirusService(context: Context) {
    val intent = Intent(context, AntivirusService::class.java).apply {
        action = AntivirusService.ACTION_STOP
    }
    try {
        context.startService(intent)
    } catch (e: SecurityException) {
        Toast.makeText(
            context,
            "Ошибка: недостаточно разрешений для остановки сервиса",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun showStoppedNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, "antivirus_channel")
        .setContentTitle("Обнаружитель APK")
        .setContentText("Защита отключена")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(pendingIntent) // Добавлено для перехода
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(2, notification)
}
