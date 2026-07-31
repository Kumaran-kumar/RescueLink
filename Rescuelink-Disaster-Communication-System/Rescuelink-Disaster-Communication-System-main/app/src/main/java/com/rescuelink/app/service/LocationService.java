package com.rescuelink.app.service;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import com.rescuelink.app.util.Constants;

/**
 * Provides GPS location updates using FusedLocationProviderClient.
 */
public class LocationService {

    private final FusedLocationProviderClient fusedLocationClient;
    private final MutableLiveData<Location> currentLocation = new MutableLiveData<>();
    private final Context context;
    private LocationCallback locationCallback;
    private boolean isTracking = false;
    private volatile Location lastKnownLocation;

    public LocationService(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public LiveData<Location> getCurrentLocation() {
        return currentLocation;
    }

    public void startLocationUpdates() {
        if (isTracking) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                Constants.LOCATION_UPDATE_INTERVAL
        )
        .setMinUpdateIntervalMillis(Constants.LOCATION_FASTEST_INTERVAL)
        .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastKnownLocation = location;
                    currentLocation.postValue(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        isTracking = true;

        // Also get last known location immediately
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                lastKnownLocation = location;
                currentLocation.postValue(location);
            }
        });
    }

    /**
     * The most recent non-null fix seen by this service, or null if none yet.
     * Used as a fallback so an SOS is not sent with a silent 0.0,0.0 (TASK-08).
     */
    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }

    public void stopLocationUpdates() {
        if (locationCallback != null && isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            isTracking = false;
        }
    }
}
