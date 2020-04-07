package com.example.android.whatstheweather.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

public class LocationServices {

    private LocationManager locationManager;
    private static final int MY_PERMISSION_REQUEST_LOCATION = 99;

    public LocationServices(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    /**
     * Return user's last known location, return null is location access is not permitted
     * @return user's last known location
     */
    public Location getLocation(Context context, final Activity activity) {
        this.promptLocationPermission(context, activity);
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return this.locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        return null;
    }


    private void promptLocationPermission(Context context, final Activity activity) {
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            /* should we show an explanation */
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION))
            {
                /* show explanation to the user about requiring location permission */
                new AlertDialog.Builder(context).setTitle("Permission Needed")
                        .setMessage("Permission needed to access location to function properly")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                /* prompt the user once explanation has been shown */
                                ActivityCompat.requestPermissions(activity,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_LOCATION);
                            }
                        }).create().show();
            }
            else
            {
                /* no explanation needed, just request the permission */
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_LOCATION);
            }
        }
    }
}