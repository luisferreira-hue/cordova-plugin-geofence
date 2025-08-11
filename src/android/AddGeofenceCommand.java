package com.cowbell.cordova.geofence;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import org.apache.cordova.LOG;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddGeofenceCommand extends AbstractGoogleServiceCommand {
    private List<Geofence> geofencesToAdd;
    private PendingIntent pendingIntent;

    public AddGeofenceCommand(Context context, PendingIntent pendingIntent,
                              List<Geofence> geofencesToAdd) {
        super(context);
        this.geofencesToAdd = geofencesToAdd;
        this.pendingIntent = pendingIntent;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void ExecuteCustomCode() {
        logger.log(Log.DEBUG, "Adding new geofences...");
        if (geofencesToAdd != null && geofencesToAdd.size() > 0) try {
            GeofencingRequest.Builder requestBuilder = new GeofencingRequest.Builder();
            requestBuilder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
            requestBuilder.addGeofences(geofencesToAdd);

            GeofencingClient geofencingClient = LocationServices.getGeofencingClient(this.context);
            geofencingClient.addGeofences(requestBuilder.build(), pendingIntent)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            logger.log(Log.DEBUG, "Geofences successfully added with geofencingClient");
                            CommandExecuted();
                        }
                    })
                    .addOnFailureListener(e -> {
                        try {
                            Map<Integer, String> errorCodeMap = new HashMap<Integer, String>();
                            errorCodeMap.put(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE, GeofencePlugin.ERROR_GEOFENCE_NOT_AVAILABLE);
                            errorCodeMap.put(GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES, GeofencePlugin.ERROR_GEOFENCE_LIMIT_EXCEEDED);

                            String message = "Adding geofences failed - Exception.Message: " + e.getMessage();
                            JSONObject error = new JSONObject();
                            error.put("message", message);

                            logger.log(Log.ERROR, message);
                            CommandExecuted(error);
                        } catch (JSONException exception) {
                            CommandExecuted(exception);
                        }
                    });
        } catch (Exception exception) {
            logger.log(LOG.ERROR, "Exception while adding geofences");
            exception.printStackTrace();
            CommandExecuted(exception);
        }
    }
}