package com.geopedometer.data.repository

import android.content.Context
import com.geopedometer.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("pedometer_profile", Context.MODE_PRIVATE)

    fun getProfile(): UserProfile {
        return UserProfile(
            stepLengthMeters = prefs.getFloat("step_length", 0.8f).toDouble(),
            heightCm = prefs.getFloat("height_cm", 175f).toDouble()
        )
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putFloat("step_length", profile.stepLengthMeters.toFloat())
            .putFloat("height_cm", profile.heightCm.toFloat())
            .apply()
    }
}
