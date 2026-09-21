package com.geopedometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geopedometer.databinding.ActivityMainBinding
import com.geopedometer.domain.model.StepDataState
import com.geopedometer.domain.repository.StepRepository
import com.geopedometer.service.PedometerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var repository: StepRepository
    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startPedometerService()
        } else {
            Toast.makeText(this, "Разрешения необходимы для работы", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
        observeState()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startPedometerService()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun startPedometerService() {
        val intent = Intent(this, PedometerService::class.java).apply {
            action = PedometerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.currentState.collect { state ->
                    when (state) {
                        is StepDataState.Idle -> {
                            binding.tvSteps.text = "0"
                            binding.tvDistance.text = "0.00 км"
                        }
                        is StepDataState.Counting -> {
                            binding.tvSteps.text = state.steps.toString()
                            binding.tvDistance.text = String.format("%.2f км", state.distanceKm)
                            binding.tvActiveTime.text = formatTime(state.activeTimeSeconds)
                        }
                        is StepDataState.GoalReached -> {
                            Toast.makeText(
                                this@MainActivity,
                                "Цель достигнута: ${state.targetName}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is StepDataState.Error -> {
                            Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
