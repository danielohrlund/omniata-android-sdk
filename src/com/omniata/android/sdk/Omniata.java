package com.omniata.android.sdk;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

public class Omniata {
	
	private static final String TAG       = "Omniata";
	private static final String EVENT_LOG = "events";
	private static final String SDK_VERSION = "android-2.0.1";
	
	private static Omniata instance;
	
	
	/**
	 * Initialize the Omniata API with different URL for different Omniata services
	 * @param context
	 * @param apiKey
	 * @param userID
	 * @param org	organization name of the URl, new url will be org.analyzer.omniata.com and org.engager.omniata.com
	 * @throws IllegalArgumentException
	 */
	public static void initialize(Context context, String apiKey, String userID, String org) throws IllegalArgumentException{
		synchronized(Omniata.class) {			
			if (instance == null) {
				OmniataLog.i(TAG, "Initializing Omniata API");
				instance = new Omniata(context, apiKey, userID, org);
			}
			/*
			 * Since this singleton may persist across application launches
			 * we need to support re-initialization of the SDK
			 */
			instance._initialize(context, apiKey, userID, org);
		}
	}
	
	public static void setLogLevel(int priority) {
		OmniataLog.setPriority(priority);
	}
	
	
	/**
	 * Tracks a parameterless event
	 * 
	 * @param eventType
	 * @throws IllegalArgumentException if eventType is null or empty
	 * @throws IllegalStateException if SDK not initialized 
	 */
	public static void track(String eventType) throws IllegalArgumentException, IllegalStateException {
		track(eventType, null);
	}

	/**
	 * Tracks an event with parameters
	 * @param eventType
	 * @param parameters
	 * @throws IllegalArgumentException	if eventType is null or empty
	 * @throws IllegalStateException	if SDK not initialized  
	 */
	public static void track(String eventType, JSONObject parameters) throws IllegalArgumentException, IllegalStateException {
		synchronized(Omniata.class) {
			assertInitialized();
			instance._track(eventType, parameters);
		}
	}
	
	/**
	 * Track Load with unity version info getting from unity code.
	 * @param para
	 */
	public static void unityTrackLoad(String para){
		trackLoad(unityJsonGenerator(para));
	}
	
	  /**
	  * Track custom event for usage in Unity
	  * @param eventType
	  * @param para				
	  */
	 public static void unityTrack(String eventType, String para){
	     instance._track(eventType, unityJsonGenerator(para));
	 }
	 
	 /**
	  * String from Unity to JSONobject, string from Unity is URL Encoded.
	  * @param para
	  * @return
	  */
	 private static JSONObject unityJsonGenerator(String para){
		 JSONObject parameters = new JSONObject();
	     String[] paraArray=para.split("\n");
	     String[] paraPair;
	     // Convert string to JsonObject
	     for (int i=0;i<paraArray.length;i++){
	         paraPair = paraArray[i].split("=");
	         try{
	         	parameters.put(URLDecoder.decode(paraPair[0], "UTF-8"), URLDecoder.decode(paraPair[1], "UTF-8"));
	            //parameters.put(paraPair[0], paraPair[1]);
	         } catch(Exception e){
	             // do something
	        	 OmniataLog.e(TAG, e.toString());
	         }
	     }
	     return parameters;
	 }
	 
	/**
	 * unity_log log method for using in Unity3D 
	 * @param message
	 */
	public static void unity_log(String message){
	   Log.i("Omniata", message);
	}
	 
	private static void assertInitialized() throws IllegalStateException{
		if (instance == null) {
			throw new IllegalStateException("Uninitialized SDK");
		}
	}
	
	/**
	 * Tracks a load event. 
	 * Should be called upon app start.
	 * @throws IllegalStateException if SDK not initialized 
	 */
	public static void trackLoad() throws IllegalStateException{
		trackLoad(null);
	}
	
	/**
	 * Tracks a load event with additional parameters
	 * @param parameters Additional parameters to track with event
	 * @throws IllegalStateException if SDK not initialized
	 */
	public static void trackLoad(JSONObject parameters) throws IllegalStateException {
		if (parameters == null) {
			parameters = new JSONObject();
		}
		track("om_load", OmniataUtils.mergeJSON(getAutomaticParameters(), parameters));
	}
	
	/**
	 * Sets the current user id used to track events
	 * @param userId
	 * @throws IllegalArgumentException if userID is null or empty
	 * @throws IllegalStateException if SDK not initialized 
	 */
	public static void setUserId(String userId) throws IllegalArgumentException, IllegalStateException {
		synchronized(Omniata.class) {
			assertInitialized();
			OmniataUtils.assertUserIdValid(userId);			
			instance._setUserId(userId);
		}
	}
	
	/**
	 * Sets the current API key used to track events
	 * @param apiKey
	 * @throws IllegalArgumentException if apiKey is null or empty
	 * @throws IllegalStateException if SDK not initialized 
	 */
	public static void setApiKey(String apiKey) throws IllegalArgumentException, IllegalStateException {
		synchronized(Omniata.class) {
			assertInitialized();
			OmniataUtils.assertApiKeyValid(apiKey);
			instance._setApiKey(apiKey);
		}
	}
	
	/**
	 * Fetches content for this user from a specific channel
	 * 
	 * @param channelId The id of this channel
	 * @param handler An object implementing OmniataChannelResponseHandler
	 * @throws IllegalStateException if SDK not initialized
	 */
	public static void channel(int channelId, OmniataChannelResponseHandler handler) throws IllegalStateException {
		synchronized(Omniata.class) {
			assertInitialized();			
			instance._channel(channelId, handler);
		}
	}
	
	/**
	 * Tracks a revenue event
	 * 
	 * @param total Revenue amount in currency code
	 * @param currencyCode A three letter currency code following ISO-4217 spec.
	 * @throws IllegalStateException if SDK not initialized 
	 */
	public static void trackRevenue(double total, String currencyCode) throws IllegalStateException {
		// TODO: add currency code validation
		trackRevenue(total, currencyCode, null);
	}
	
	/**
	 * Tracks a revenue event
	 * 
	 * @param total Revenue amount in currency code
	 * @param currencyCode A three letter currency code following ISO-4217 spec.
	 * @param additionalParams Additional parameters to be tracked with event
	 * @throws IllegalStateException if SDK not initialized 
	 */
	public static void trackRevenue(double total, String currencyCode, JSONObject additionalParams) throws IllegalStateException {
		JSONObject parameters = new JSONObject();

		try {
			parameters.put("total", total); // Java doesn't use locale-specific formatting, so this is safe
			parameters.put("currency_code", currencyCode);
			
			if (additionalParams != null) {
				@SuppressWarnings("unchecked")
				Iterator<String> i = (Iterator<String>)additionalParams.keys();
				while(i.hasNext()) {
					String key = (String)i.next();
					Object val = additionalParams.get(key);
					parameters.put(key, val);
				}
			}
			
			track("om_revenue", parameters);
			
		} catch (JSONException e) {
			OmniataLog.e(TAG, e.toString());
		}
	}
	
	public static void enablePushNotifications(String registrationId) {
		JSONObject params = new JSONObject();
		try {
			params.put("om_registration_id", registrationId);
			track("om_gcm_enable", params);
		} catch (JSONException e) {
			OmniataLog.e(TAG, e.toString());
		}
	}
	
	public static void disablePushNotifications() {
		track("om_gcm_disable");
	}
	
	protected static JSONObject getAutomaticParameters() {
		JSONObject properties = new JSONObject();
		Locale locale = Locale.getDefault();
		
		try {
			// Standard automatic parameters
			properties.put("om_sdk_version", SDK_VERSION);
			properties.put("om_os_version", android.os.Build.VERSION.SDK_INT);
			properties.put("om_platform", "android");
			properties.put("om_device", android.os.Build.MODEL);
			
			// Android-specific parameters
			properties.put("om_android_id", Settings.Secure.ANDROID_ID);
			properties.put("om_android_serial", android.os.Build.SERIAL);
			properties.put("om_android_device", android.os.Build.DEVICE);
			properties.put("om_android_hardware", android.os.Build.HARDWARE);
		
			if (locale != null) {
				properties.put("om_locale", locale);
			}
		} catch(Throwable e) {
			
		}
		return properties;
	}
	
	protected void _track(String eventType, JSONObject parameters) throws IllegalArgumentException {
		JSONObject event;
		
		OmniataUtils.assertValidEventType(eventType);
		
		try {			
			if (parameters != null) {
				event = new JSONObject(parameters.toString());
			} else {
				event = new JSONObject();
			}
			
			event.put("om_event_type", eventType);
			event.put("api_key", apiKey);
			event.put("uid", userID);
			event.put("om_creation_time", System.currentTimeMillis());
			
			while(true) {
				try {
					eventBuffer.put(event);
					break;
				} catch (InterruptedException e) {
				}
			}
		} catch (JSONException e) {
			OmniataLog.e(TAG, e.toString());
		}
	}
	
	protected void _channel(final int channelId, final OmniataChannelResponseHandler handler) {
		Thread req = new Thread(new Runnable() {
			
			@Override
			public void run() {
				String uri = OmniataUtils.getChannelAPI(true) + "?api_key=" + apiKey + "&uid=" + userID + "&channel_id=" + channelId;
				
				try {
					URL url = new URL(uri);
					final HttpURLConnection connection = (HttpURLConnection)url.openConnection();
					
					final int httpResponse = connection.getResponseCode();
					
					if (httpResponse >= 200 && httpResponse < 300) {
						try {
							String body = OmniataUtils.convertStreamToString(connection.getInputStream());
							JSONObject jsonObj =  new JSONObject(body);
							JSONArray content   = jsonObj.getJSONArray("content");
							handler.onSuccess(channelId, content);
							Log.i("Omniata", content.toString());
						} catch (Exception e) {
							handler.onError(channelId, e);
						}
						
					} else {
						handler.onError(channelId, new Exception("Error: Invalid http response code: " + httpResponse));
					}
				} catch (final Exception e) {
					handler.onError(channelId, e);
				}
				
			}
		});
		
		req.start();
	}
	
	private void _setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
	
	private void _setUserId(String userId) {
		this.userID = userId;
	}
	
	
	private Omniata(Context context, String apiKey, String userID, String org) {

	}
	
	
	private void _initialize(Context context, String apiKey, String userID, String org) throws IllegalArgumentException, IllegalStateException {
		OmniataLog.i(TAG, "Initializing Omniata with apiKey: " + apiKey + " and userID: " + userID);
		
		if (context == null) {
			throw new IllegalArgumentException("Activity is null");
		}
		
		OmniataUtils.assertApiKeyValid(apiKey);
		OmniataUtils.assertUserIdValid(userID);
		OmniataUtils.setURL(org);

		this.apiKey   	  = apiKey;
		this.userID   	  = userID;
		
		if (this.context == null) {
			this.context = context;
		}
		
		if (eventBuffer == null) {
			eventBuffer = new LinkedBlockingQueue<JSONObject>();
		}
		
		if (eventLog == null) {
			eventLog = new PersistentBlockingQueue<JSONObject>(context, EVENT_LOG, JSONObject.class);
		}
		
		if (eventLogger == null) {
			eventLogger = new OmniataEventLogger(eventBuffer, eventLog);
		}
		
		if (eventWorker == null) {
			eventWorker = new OmniataEventWorker(context, eventLog);
		}
		
		eventLogger.start();
		eventWorker.start();
	}
	
	private Context 							context;
	private String 								apiKey;
	private String 								userID;	
	private BlockingQueue<JSONObject> 			eventBuffer;
	private PersistentBlockingQueue<JSONObject> eventLog;
	private OmniataEventLogger					eventLogger;
	private OmniataEventWorker					eventWorker;
}
