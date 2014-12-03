package com.dispatcher;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.Intent;

import android.location.LocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.os.Handler;
import android.os.Looper;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpResponse;

import com.red_folder.phonegap.plugin.backgroundservice.BackgroundService;

public class MyService extends BackgroundService {
	
	private final static String TAG = MyService.class.getSimpleName();
	
	private MyLocationListener locationListener = null;
	
		
	HttpClient httpClient = new DefaultHttpClient();
	HttpPost keepAlivePost = null;
	HttpPost updateLocationPost = null;
	String phoneNumber = null;
	
	@Override
	public void onStart(Intent intent, int startId) {  
		Log.d(TAG, "Started service!");    
		
		phoneNumber = getPrimaryPhoneNumber();
			
		Boolean updateLocationOnLocationChange = getSettingsBool("updateLocationOnLocationChange", false);
		String updateLocationURL = getSettingsString("updateLocationURL", null);
		String keepAliveURL = getSettingsString("keepAliveURL", null);
		Integer updateThresholdInMs = getSettingsInteger("updateThresholdInMs", 2 * 1000 * 60);
		Integer updateThresholdInMeters = getSettingsInteger("updateThresholdInMeters", 1000);
		
		if(updateLocationURL != null)
			updateLocationPost = new HttpPost(updateLocationURL);
			
		if(keepAliveURL != null)
			keepAlivePost = new HttpPost(keepAliveURL);
		
		if(updateLocationOnLocationChange)
			installLocationListenerIfNotAlreadyInstalled(updateThresholdInMs, updateThresholdInMeters);		

		sendKeepAliveToServerAsync();
	}

	private String getPrimaryPhoneNumber() 
	{
		TelephonyManager tMgr = (TelephonyManager)getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
		return tMgr.getLine1Number();
	}

	@Override
	protected JSONObject doWork() {
		JSONObject result = new JSONObject();
		
		sendKeepAliveToServerAsync();
				
		return result;	
	}
	
	@Override
	protected JSONObject getConfig() {
		JSONObject result = new JSONObject();
		

		
		return result;
	}

	@Override
	protected void setConfig(JSONObject config) {
		try {	
			Boolean updateLocationOnLocationChange = config.getBoolean("updateLocationOnLocationChange");
			Integer updateThresholdInMs = config.getInt("updateThresholdInMs");
			Integer updateThresholdInMeters = config.getInt("updateThresholdInMeters");
			String updateLocationURL = config.getString("updateLocationURL");
			String keepAliveURL = config.getString("keepAliveURL");
			
			if(updateLocationOnLocationChange)
				installLocationListenerIfNotAlreadyInstalled(updateThresholdInMs, updateThresholdInMeters);	

			
			if(updateLocationURL != null)
				updateLocationPost = new HttpPost(updateLocationURL);
							
			
			if(keepAliveURL != null)
				keepAlivePost = new HttpPost(keepAliveURL);
						
			
			storeSettingsBool("updateLocationOnLocationChange", updateLocationOnLocationChange);
			storeSettingsInteger("updateThresholdInMs", updateThresholdInMs);
			storeSettingsInteger("updateThresholdInMeters", updateThresholdInMeters);
			storeSettingsString("updateLocationURL", updateLocationURL);
			storeSettingsString("keepAliveURL", keepAliveURL);	
			
		} catch (Exception e) {
			Log.e(TAG, "setConfig failed: " + e.getMessage());
		}
		
	}     
	
	private void installLocationListenerIfNotAlreadyInstalled(int updateThresholdInMs, double updateThresholdInMeters)
	{
		if(locationListener != null)
		{
			locationListener.updateThresholdInMs = updateThresholdInMs;
			locationListener.updateThresholdInMeters = updateThresholdInMeters;
			return;	
		}
		
		locationListener = new MyLocationListener(updateThresholdInMs, updateThresholdInMeters);					
					
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, (long)updateThresholdInMs, (float)updateThresholdInMeters, locationListener, Looper.getMainLooper());
		//locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, (long)updateThresholdInMs, (float)updateThresholdInMeters, locationListener, Looper.getMainLooper());
	}
	
	private void storeSettingsBool(String settingsName, Boolean value) 
	{
		Context ctx = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(settingsName, value);
		editor.commit();
	}
	
	private void storeSettingsInteger(String settingsName, Integer value) 
	{
		Context ctx = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(settingsName, value);
		editor.commit();
	}
	
	private void storeSettingsString(String settingsName, String value) 
	{
		Context ctx = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(settingsName, value);
		editor.commit();
	}
	
	
	
	private Boolean getSettingsBool(String settingsName, Boolean defaultValue)
	{
		Context ctx = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		return prefs.getBoolean(settingsName, defaultValue);
	}
	
	private Integer getSettingsInteger(String settingsName, Integer defaultValue)
	{
		Context ctx = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		return prefs.getInt(settingsName, defaultValue);
	}
	
	private String getSettingsString(String settingsName, String defaultValue)
	{
		Context ctx = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		return prefs.getString(settingsName, defaultValue);
	}


	@Override
	protected JSONObject initialiseLatestResult() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void onTimerEnabled() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onTimerDisabled() {
		// TODO Auto-generated method stub
		
	}
	
	protected void onLocationUpdate(Location loc) 
	{
		sendLocationUpdateToServerAsync(loc);
	}
	
	
	private void sendLocationUpdateToServerAsync(Location loc)
	{
		class MyRunnable implements Runnable {
			private Location loc;
			public MyRunnable(Location loc) {
				this.loc = loc;
			}

			public void run() 
			{
				sendLocationUpdateToServer(loc);			
			}
		}
	
		new Thread(new MyRunnable(loc)).start();
	}
	
	private void sendLocationUpdateToServer(Location loc)
	{
		if(updateLocationPost == null)
			return;
			
		try {
			List<NameValuePair> pairs = new ArrayList<NameValuePair>();
			pairs.add(new BasicNameValuePair("latitude", Double.toString(loc.getLatitude())));
			pairs.add(new BasicNameValuePair("longitude", Double.toString(loc.getLongitude())));
			pairs.add(new BasicNameValuePair("driverId", phoneNumber));
			
			updateLocationPost.setEntity(new UrlEncodedFormEntity(pairs));
			
			HttpResponse response = httpClient.execute(updateLocationPost);
			Log.i(TAG, "sendLocationUpdateToServer successfully executed: Lat: " + loc.getLatitude() + ", Lng: " + loc.getLongitude() + ", Phone: " + phoneNumber);
		}
		catch(Exception ex) {
			Log.e(TAG, "sendLocationUpdateToServer failed: " + ex.getMessage() + " " + ex.toString());
		}				
	}
	
	private void sendKeepAliveToServerAsync()
	{
		class MyRunnable implements Runnable {
			public void run() 
			{
				sendKeepAliveToServer();			
			}
		}
	
		new Thread(new MyRunnable()).start();
	}
	
	private void sendKeepAliveToServer()
	{
		if(keepAlivePost == null)
			return;
	
		Location loc = getCurrentLocation();
		if(loc == null)
		{
			Log.d(TAG, "sendKeepAliveToServer failed because no last known location could be attained.");
			return;
		}
			
		try {
			List<NameValuePair> pairs = new ArrayList<NameValuePair>();
			pairs.add(new BasicNameValuePair("latitude", Double.toString(loc.getLatitude())));
			pairs.add(new BasicNameValuePair("longitude", Double.toString(loc.getLongitude())));
			pairs.add(new BasicNameValuePair("driverId", phoneNumber));
			
			keepAlivePost.setEntity(new UrlEncodedFormEntity(pairs));
			HttpResponse response = httpClient.execute(keepAlivePost);
			Log.i(TAG, "sendKeepAliveToServer successfully executed: Lat: " + loc.getLatitude() + ", Lng: " + loc.getLongitude() + ", Phone: " + phoneNumber);
		}
		catch(Exception ex) {
			Log.e(TAG, "sendKeepAliveToServer failed: " + ex.getMessage() + " " + ex.toString());
		}			
	}
	
	private Location getCurrentLocation()
	{
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if(loc != null)
			return loc;
			
		return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
	}

	public class MyLocationListener implements LocationListener
	{
		public Location previousBestLocation = null;
		private static final int TWO_MINUTES = 1000 * 60 * 2;
		public int updateThresholdInMs;
		public double updateThresholdInMeters;

		public MyLocationListener(int updateThresholdInMs, double updateThresholdInMeters)
		{
			this.updateThresholdInMs = updateThresholdInMs;
			this.updateThresholdInMeters = updateThresholdInMeters;
		}
		

		public void onLocationChanged(final Location loc)
		{
			Log.i(TAG, "Location changed, Lat: " + loc.getLatitude() + " Lng: " + loc.getLongitude());
			if(isBetterLocation(loc, previousBestLocation)) {
					previousBestLocation = loc;
					onLocationUpdate(loc);
			}                               
		}

		public void onProviderDisabled(String provider)
		{
			
		}


		public void onProviderEnabled(String provider)
		{
			
		}
		
		public void onStatusChanged(String provider, int status, Bundle extras)
		{

		}
		
		private boolean isBetterLocation(Location location, Location currentBestLocation) {
			if (currentBestLocation == null) {
				// A new location is always better than no location
				return true;
			}

			// Check whether the new location fix is newer or older
			long timeDelta = location.getTime() - currentBestLocation.getTime();
			
			if(timeDelta < updateThresholdInMs)
				return false;
				
			Log.i(TAG, "Passed isBetterLocation timeDelta: " + timeDelta + " threshold: " + updateThresholdInMs);	
			
			boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
			boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
			boolean isNewer = timeDelta > 0;

			// If it's been more than two minutes since the current location, use the new location
			// because the user has likely moved
			if (isSignificantlyNewer) {
				return true;
			// Return if it's not significantly newer
			} else if (isSignificantlyOlder) {
				return false;
			}

			
			// Check whether the new location fix is more or less accurate
			int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
			boolean isLessAccurate = accuracyDelta > 0;
			boolean isMoreAccurate = accuracyDelta < 0;
			boolean isSignificantlyLessAccurate = accuracyDelta > 200;

			// Check if the old and new location are from the same provider
			boolean isFromSameProvider = isSameProvider(location.getProvider(),
					currentBestLocation.getProvider());

			// Determine location quality using a combination of timeliness and accuracy
			if (isMoreAccurate) {
				return true;
			} else if (isNewer && !isLessAccurate) {
				return true;
			} else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
				return true;
			}
			return false;
		}
		
		private boolean isSameProvider(String provider1, String provider2) {
			if (provider1 == null) {
			  return provider2 == null;
			}
			return provider1.equals(provider2);
		}
	}
}
