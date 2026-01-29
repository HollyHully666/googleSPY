package com.example.googlespy.service

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * One-shot location for GPS relay: last known first, then current with timeout, then single update.
 */
class LocationHelper(private val context: Context) {

    private val fused: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getLastLocation(callback: (lat: Double?, lon: Double?, error: String?) -> Unit) {
        fused.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    callback(location.latitude, location.longitude, null)
                } else {
                    requestCurrentWithTimeout(callback)
                }
            }
            .addOnFailureListener { _ ->
                requestCurrentWithTimeout(callback)
            }
    }

    private fun requestCurrentWithTimeout(callback: (lat: Double?, lon: Double?, error: String?) -> Unit) {
        val cts = CancellationTokenSource()
        mainHandler.postDelayed({
            cts.cancel()
        }, 15000L)
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { current: Location? ->
                if (current != null) {
                    callback(current.latitude, current.longitude, null)
                } else {
                    requestSingleUpdate(callback)
                }
            }
            .addOnFailureListener { _ ->
                requestSingleUpdate(callback)
            }
    }

    private fun requestSingleUpdate(callback: (lat: Double?, lon: Double?, error: String?) -> Unit) {
        var completed = false
        fun doCallback(lat: Double?, lon: Double?, err: String?) {
            if (completed) return
            completed = true
            callback(lat, lon, err)
        }
        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    fused.removeLocationUpdates(this)
                    doCallback(loc.latitude, loc.longitude, null)
                }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMaxUpdates(1)
            .setDurationMillis(15000L)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        mainHandler.postDelayed({
            if (!completed) {
                fused.removeLocationUpdates(locationCallback)
                doCallback(null, null, "no_location")
            }
        }, 16000L)
    }
}
