package com.omniata.android.sdk;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONObject;

import android.app.Activity;

class OmniataEventWorker implements Runnable {
	private static final String TAG 			      	= "OmniataEventWorker";
	private static final int    CONNECTION_TIMEOUT 		= 30 * 1000;
	private static final int    READ_TIMEOUT 	   		= 30 * 1000;
	private static final int	SLEEP_TIME		   		= 1 * 1000;
	private static final int	MAX_SLEEP		   		= 64 * 1000;
	private static final int	RETRY_CONNECTIVITY_TIME = 16 * 1000;

	private Activity 							activity;
	private int 								connectionTimeout;
	private int 								readTimeout;
	private boolean 							debug;	
	private PersistentBlockingQueue<JSONObject> eventLog;
	private int									retries;
	private Thread								worker;
	private boolean								isRunning;
	private boolean								isStarted;

	public OmniataEventWorker(Activity activity, PersistentBlockingQueue<JSONObject> eventLog, boolean debug) {
		this.activity 		   = activity;
		this.eventLog   	   = eventLog;
		this.connectionTimeout = CONNECTION_TIMEOUT;
		this.readTimeout 	   = READ_TIMEOUT;
		this.debug 			   = debug;
		this.retries		   = 0;
		this.worker            = new Thread(this);
	}
	
	public void start() {
		if (!isStarted) {
			this.worker.start();
			isStarted = true;
		}
	}

	/**
	 * Returns the amount of time thread should sleep before attempting to resend.
	 * Will back off exponentially to prevent pegging servers in case of downtime
	 */
	protected int sleepTime(int retries) {
		return Math.min(MAX_SLEEP, SLEEP_TIME << retries);
	}

	/**
	 * Causes thread to sleep based on retry count
	 * @param retries
	 */
	protected void throttle(int retries) throws InterruptedException {
		int timeSleepMS = sleepTime(retries);
		sleep(timeSleepMS);
	}

	protected void sleep(int timeMS) throws InterruptedException {
		OmniataLog.i(TAG, "Retrying in " + timeMS + "ms");
		Thread.sleep(timeMS);
	}

	@Override
	public void run() {
		OmniataLog.i(TAG, "Thread begin");
		isRunning = true;
		try {
			while(isRunning) {
				OmniataLog.v(TAG, "Thread running: " + Thread.currentThread().getId());
				// Check for network connectivity prior to processing events
				if (OmniataUtils.isConnected(activity)) {
					OmniataLog.v(TAG, "Connection available");
					processEvents();
				} else {
					OmniataLog.v(TAG, "Connection unavailable");
					sleep(RETRY_CONNECTIVITY_TIME);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		OmniataLog.i(TAG, "Thread done");
	}

	protected void processEvents() throws InterruptedException {
		JSONObject event = eventLog.blockingPeek();
		if (sendEvent(event)) {
			retries = 0;
			eventLog.take();
		} else {
			throttle(retries++);
		}
	}

	protected boolean sendEvent(JSONObject event) {
		HttpURLConnection connection = null;

		try {
			String query    = OmniataUtils.jsonToQueryString(event);
			String eventURL = OmniataUtils.getEventAPI(false, debug) + "?" + query;
			
			OmniataLog.i(TAG, "Calling event endpoint: " + eventURL);
			URL url 		= new URL(eventURL);

			connection = (HttpURLConnection)url.openConnection();

			connection.setConnectTimeout(connectionTimeout);
			connection.setReadTimeout(readTimeout);

			connection.getInputStream();

			int httpResponseCode 	   = connection.getResponseCode();
			String httpResponseMessage = connection.getResponseMessage();

			OmniataLog.d(TAG, "" + httpResponseCode + ": " + httpResponseMessage);

			if (httpResponseCode >= 500) {
				// 5xx Server Error
				/* Will retry */
			} else if (httpResponseCode >= 400) {
				// 4xx Client Error
				/* Will not retry */
				return true;
			} else if (httpResponseCode >= 300) {
				// 3xx Redirection
			} else if (httpResponseCode >= 200) {
				// 2xx Success
				return true;
			} else {
				// 1xx Informational
			}		
		} catch (MalformedURLException e) {
			OmniataLog.e(TAG, e.toString());
		} catch (IOException e) {
			OmniataLog.e(TAG, e.toString());
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

		return false;
	}
}