package com.cowbell.cordova.geofence;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.Manifest;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GeofencePlugin extends CordovaPlugin {
    public static final String TAG = "cordova-geofence";

    public static final String ERROR_UNKNOWN = "UNKNOWN";
    public static final String ERROR_PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String ERROR_GEOFENCE_NOT_AVAILABLE = "GEOFENCE_NOT_AVAILABLE";
    public static final String ERROR_GEOFENCE_LIMIT_EXCEEDED = "GEOFENCE_LIMIT_EXCEEDED";

    private GeoNotificationManager geoNotificationManager;
    public static CordovaWebView webView = null;

    private class Action {
        public String action;
        public JSONArray args;
        public CallbackContext callbackContext;

        public Action(String action, JSONArray args, CallbackContext callbackContext) {
            this.action = action;
            this.args = args;
            this.callbackContext = callbackContext;
        }
    }

    //FIXME: what about many executedActions at once
    private Action executedAction;

    /**
     * @param cordova
     *            The context of the main Activity.
     * @param webView
     *            The associated CordovaWebView.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        GeofencePlugin.webView = webView;
        Context context = this.cordova.getActivity().getApplicationContext();
        Logger.setLogger(new Logger(TAG, context, false));
        geoNotificationManager = new GeoNotificationManager(context);
    }

    @Override
    public boolean execute(final String action, final JSONArray args,
                           final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "GeofencePlugin execute action: " + action + " args: " + args.toString());
        executedAction = new Action(action, args, callbackContext);

        cordova.getThreadPool().execute(() -> {
            switch (action) {
                case "addOrUpdate": {
                    List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();
                    for (int i = 0; i < args.length(); i++) {
                        GeoNotification not = parseFromJSONObject(args.optJSONObject(i));
                        if (not != null) {
                            geoNotifications.add(not);
                        }
                    }
                    geoNotificationManager.addGeoNotifications(geoNotifications, callbackContext);
                    break;
                }
                case "remove":
                    List<String> ids = new ArrayList<String>();
                    for (int i = 0; i < args.length(); i++) {
                        ids.add(args.optString(i));
                    }
                    geoNotificationManager.removeGeoNotifications(ids, callbackContext);
                    break;
                case "removeAll":
                    geoNotificationManager.removeAllGeoNotifications(callbackContext);
                    break;
                case "getWatched": {
                    List<GeoNotification> geoNotifications = geoNotificationManager.getWatched();
                    callbackContext.success(Gson.get().toJson(geoNotifications));
                    break;
                }
                case "initialize":
                    initialize();
                    break;
                case "requestPermissions":
                    requestPermissions(callbackContext);
                    break;
                case "deviceReady":
                    deviceReady();
                    break;
            }
        });

        return true;
    }

    public boolean execute(Action action) throws JSONException {
        return execute(action.action, action.args, action.callbackContext);
    }

    private GeoNotification parseFromJSONObject(JSONObject object) {
        GeoNotification geo = GeoNotification.fromJson(object.toString());
        return geo;
    }

    public static void onTransitionReceived(List<GeoNotification> notifications) {
        Log.d(TAG, "Transition Event Received!");
        String js = "setTimeout('geofence.onTransitionReceived("
            + Gson.get().toJson(notifications) + ")',0)";
        if (webView == null) {
            Log.d(TAG, "Webview is null");
        } else {
            webView.sendJavascript(js);
        }
    }

    private void deviceReady() {
        Intent intent = cordova.getActivity().getIntent();
        String data = intent.getStringExtra("geofence.notification.data");
        String js = "setTimeout('geofence.onNotificationClicked(" + data + ")',0)";

        if (data == null) {
            Log.d(TAG, "No notifications clicked.");
        } else {
            webView.sendJavascript(js);
        }
    }

    private void initialize() {
		Log.d(TAG, "Initialize empty");
    }
	
    private void requestPermissions(CallbackContext callbackContext) {
		Log.d(TAG, "requestPermissions");
        String[] permissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        };

        if (!hasPermissions(permissions)) {
			Log.d(TAG, "No permissions, request");
            PermissionHelper.requestPermissions(this, 0, permissions);
        } else {
			Log.d(TAG, "Has permissions, doing nothing");
            callbackContext.success();
        }
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (!PermissionHelper.hasPermission(this, permission)) return false;
        }

        return true;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        PluginResult result;

        if (executedAction != null) {
            for (int r:grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                    executedAction.callbackContext.sendPluginResult(result);
                    executedAction = null;
                    return;
                }
            }
            Log.d(TAG, "Permission Granted!");
            execute(executedAction);
            executedAction = null;
        }
    }
}