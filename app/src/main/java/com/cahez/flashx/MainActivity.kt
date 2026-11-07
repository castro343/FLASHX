package com.cahez.flashx

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var torchOn = false
    private var supportedStrength = false
    private var maxStrength = 1
    private var strength = 1
    private var intervalMs = 500
    private var pattern = "steady"
    private var frameIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var powerButton: Button
    private lateinit var strengthLabel: TextView
    private lateinit var rateLabel: TextView
    private lateinit var strengthBar: SeekBar
    private lateinit var rateBar: SeekBar

    private val cameraCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) {
                torchOn = enabled
                runOnUiThread { updatePowerButton() }
            }
        }
        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) {
                torchOn = false
                runOnUiThread {
                    updatePowerButton()
                    status.text = "Flashlight temporarily unavailable"
                }
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (pattern == "steady") return
            val frames = when (pattern) {
                "double" -> listOf(true to 140, false to 130, true to 140, false to 650)
                "beacon" -> listOf(true to 260, false to 740)
                "sos" -> {
                    val unit = intervalMs.coerceIn(100, 700)
                    listOf(
                        true to unit, false to unit,
                        true to unit, false to unit,
                        true to unit, false to (unit * 3),
                        true to (unit * 3), false to unit,
                        true to (unit * 3), false to unit,
                        true to (unit * 3), false to (unit * 3),
                        true to unit, false to unit,
                        true to unit, false to unit,
                        true to unit, false to (unit * 5)
                    )
                }
                else -> listOf(true to intervalMs, false to intervalMs)
            }
            val frame = frames[frameIndex % frames.size]
            if (frame.first) turnOn() else turnOff()
            frameIndex++
            handler.postDelayed(this, frame.second.toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraManager.registerTorchCallback(cameraCallback, handler)
        buildInterface()
        discoverFlash()
    }

    private fun buildInterface() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF080D16.toInt())
        }
        fun label(text: String, size: Float = 16f): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(0xFFEAF2FF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 10)
            }
        page.addView(label("FLASHX", 30f))
        page.addView(label("PROGRAMMABLE TORCH CONTROL", 11f))
        status = label("Detecting flashlight…", 14f)
        page.addView(status)

        powerButton = Button(this).apply {
            text = "FLASHLIGHT OFF"
            textSize = 18f
            setOnClickListener {
                if (pattern != "steady") stopPattern()
                if (torchOn) turnOff() else turnOn()
            }
        }
        page.addView(powerButton, LinearLayout.LayoutParams(-1, 64).apply {
            setMargins(0, 22, 0, 22)
        })

        strengthLabel = label("Intensity: checking support")
        page.addView(strengthLabel)
        strengthBar = SeekBar(this).apply {
            max = 9
            progress = 0
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    strength = progress + 1
                    strengthLabel.text = "Intensity: $strength / $maxStrength"
                    if (torchOn && supportedStrength && fromUser) turnOn()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        page.addView(strengthBar, LinearLayout.LayoutParams(-1, -2))

        rateLabel = label("Flash interval: 500 ms")
        page.addView(rateLabel)
        rateBar = SeekBar(this).apply {
            max = 19
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    intervalMs = 100 + progress * 100
                    rateLabel.text = "Flash interval: $intervalMs ms"
                    if (fromUser && pattern != "steady") restartPattern()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        page.addView(rateBar, LinearLayout.LayoutParams(-1, -2))

        page.addView(label("LIGHT EFFECTS", 13f))
        val effects = listOf(
            "Steady" to "steady",
            "Strobe" to "strobe",
            "SOS" to "sos",
            "Double pulse" to "double",
            "Beacon" to "beacon"
        )
        effects.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { (title, key) ->
                val button = Button(this).apply {
                    text = title
                    setOnClickListener { selectPattern(key) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, 54, 1f).apply {
                    setMargins(4, 4, 4, 4)
                })
            }
            if (rowItems.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 54, 1f))
            page.addView(row)
        }

        val stop = Button(this).apply {
            text = "STOP ALL EFFECTS"
            setOnClickListener { stopPattern(); turnOff() }
        }
        page.addView(stop, LinearLayout.LayoutParams(-1, 54).apply { setMargins(0, 18, 0, 0) })
        page.addView(label("Use responsibly. Avoid shining the light into anyone’s eyes.", 11f))
        setContentView(page)
    }

    private fun discoverFlash() {
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                status.text = "No camera flashlight detected on this device."
                powerButton.isEnabled = false
                return
            }
            if (Build.VERSION.SDK_INT >= 33) {
                val chars = cameraManager.getCameraCharacteristics(cameraId!!)
                maxStrength = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                supportedStrength = maxStrength > 1
            }
            strengthBar.max = (maxStrength - 1).coerceAtLeast(0)
            strengthBar.isEnabled = supportedStrength
            strength = 1
            strengthLabel.text = if (supportedStrength)
                "Intensity: 1 / $maxStrength"
            else "Intensity control: not exposed by this phone; ON/OFF still works"
            status.text = "Flashlight ready"
        } catch (e: Exception) {
            status.text = "Could not access flashlight: ${e.message ?: "permission/device error"}"
        }
    }

    private fun turnOn() {
        val id = cameraId ?: return
        try {
            if (Build.VERSION.SDK_INT >= 33 && supportedStrength) {
                cameraManager.turnOnTorchWithStrengthLevel(id, strength.coerceIn(1, maxStrength))
            } else {
                cameraManager.setTorchMode(id, true)
            }
        } catch (e: SecurityException) {
            requestCameraPermission()
        } catch (e: Exception) {
            status.text = "Torch error: ${e.message ?: "unavailable"}"
        }
        updatePowerButton()
    }

    private fun turnOff() {
        val id = cameraId ?: return
        try { cameraManager.setTorchMode(id, false) }
        catch (_: Exception) {}
        torchOn = false
        updatePowerButton()
    }

    private fun updatePowerButton() {
        if (!::powerButton.isInitialized) return
        powerButton.text = if (torchOn) "FLASHLIGHT ON — TAP TO TURN OFF" else "FLASHLIGHT OFF"
    }

    private fun selectPattern(name: String) {
        stopPattern()
        pattern = name
        if (name == "steady") {
            turnOn()
            status.text = "Steady light enabled"
        } else {
            frameIndex = 0
            status.text = "${name.replaceFirstChar { it.uppercase() }} effect running"
            handler.post(ticker)
        }
    }

    private fun restartPattern() {
        if (pattern != "steady") {
            handler.removeCallbacks(ticker)
            frameIndex = 0
            handler.post(ticker)
        }
    }

    private fun stopPattern() {
        handler.removeCallbacks(ticker)
        pattern = "steady"
        frameIndex = 0
    }

    private fun requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 42)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            status.text = "Permission granted — tap the flashlight button again."
        } else if (requestCode == 42) {
            Toast.makeText(this, "Camera permission is needed to control the torch.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPattern()
        turnOff()
    }

    override fun onDestroy() {
        stopPattern()
        turnOff()
        try { cameraManager.unregisterTorchCallback(cameraCallback) } catch (_: Exception) {}
        super.onDestroy()
    }
}
