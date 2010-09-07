package com.phonegap;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;
import android.content.Intent;
import android.webkit.WebView;

/**
 * This class listens to the compass sensor and stores the latest heading value.
 */
public class CompassListener implements SensorEventListener, Plugin{

	public static int STOPPED = 0;
	public static int STARTING = 1;
    public static int RUNNING = 2;
    public static int ERROR_FAILED_TO_START = 3;
    
    public long TIMEOUT = 30000;		// Timeout in msec to shut off listener
	
    WebView webView;					// WebView object
    DroidGap ctx;						// DroidGap object

    int status;							// status of listener
    float heading;						// most recent heading value
    long timeStamp;						// time of most recent value
    long lastAccessTime;				// time the value was last retrieved
	
    private SensorManager sensorManager;// Sensor manager
    Sensor mSensor;						// Compass sensor returned by sensor manager
	
	/**
	 * Constructor.
	 */
	public CompassListener() {
        this.timeStamp = 0;
        this.status = CompassListener.STOPPED;
	}

	/**
	 * Sets the context of the Command. This can then be used to do things like
	 * get file paths associated with the Activity.
	 * 
	 * @param ctx The context of the main Activity.
	 */
	public void setContext(DroidGap ctx) {
		this.ctx = ctx;
        this.sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
	}

	/**
	 * Sets the main View of the application, this is the WebView within which 
	 * a PhoneGap app runs.
	 * 
	 * @param webView The PhoneGap WebView
	 */
	public void setView(WebView webView) {
		this.webView = webView;
	}

	/**
	 * Executes the request and returns CommandResult.
	 * 
	 * @param action The command to execute.
	 * @param args JSONArry of arguments for the command.
	 * @return A CommandResult object with a status and message.
	 */
	public PluginResult execute(String action, JSONArray args) {
		PluginResult.Status status = PluginResult.Status.OK;
		String result = "";		
		
		try {
			if (action.equals("start")) {
				this.start();
			}
			else if (action.equals("stop")) {
				this.stop();
			}
			else if (action.equals("getStatus")) {
				int i = this.getStatus();
				return new PluginResult(status, i);
			}
			else if (action.equals("getHeading")) {
				float f = this.getHeading();
				return new PluginResult(status, f);
			}
			else if (action.equals("setTimeout")) {
				this.setTimeout(args.getLong(0));
			}
			else if (action.equals("getTimeout")) {
				long l = this.getTimeout();
				return new PluginResult(status, l);
			}
			return new PluginResult(status, result);
		} catch (JSONException e) {
			e.printStackTrace();
			return new PluginResult(PluginResult.Status.JSON_EXCEPTION);
		}
	}

	/**
     * Called when the system is about to start resuming a previous activity. 
     */
    public void onPause() {
    }

    /**
     * Called when the activity will start interacting with the user. 
     */
    public void onResume() {
    }
    
    /**
     * Called when listener is to be shut down and object is being destroyed.
     */
	public void onDestroy() {
		this.stop();
	}

    /**
     * Called when an activity you launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it. 
     * 
     * @param requestCode		The request code originally supplied to startActivityForResult(), 
     * 							allowing you to identify who this result came from.
     * @param resultCode		The integer result code returned by the child activity through its setResult().
     * @param data				An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Start listening for compass sensor.
     * 
     * @return 			status of listener
     */
	public int start() {
		
		// If already starting or running, then just return
        if ((this.status == CompassListener.RUNNING) || (this.status == CompassListener.STARTING)) {
        	return this.status;
        }

		// Get accelerometer from sensor manager
		List<Sensor> list = this.sensorManager.getSensorList(Sensor.TYPE_ORIENTATION);

        // If found, then register as listener
		if (list.size() > 0) {
			this.mSensor = list.get(0);
			this.sensorManager.registerListener(this, this.mSensor, SensorManager.SENSOR_DELAY_NORMAL);
            this.status = CompassListener.STARTING;
            this.lastAccessTime = System.currentTimeMillis();
		}

		// If error, then set status to error
        else {
            this.status = CompassListener.ERROR_FAILED_TO_START;
        }
        
        return this.status;
	}
	
    /**
     * Stop listening to compass sensor.
     */
	public void stop() {
        if (this.status != CompassListener.STOPPED) {
        	this.sensorManager.unregisterListener(this);
        }
        this.status = CompassListener.STOPPED;
	}
	
	
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub	
	}

    /**
     * Sensor listener event.
     * 
     * @param SensorEvent event
     */
	public void onSensorChanged(SensorEvent event) {

		// We only care about the orientation as far as it refers to Magnetic North
		float heading = event.values[0];

		// Save heading
        this.timeStamp = System.currentTimeMillis();
		this.heading = heading;
		this.status = CompassListener.RUNNING;

		// If heading hasn't been read for TIMEOUT time, then turn off compass sensor to save power
		if ((this.timeStamp - this.lastAccessTime) > this.TIMEOUT) {
			this.stop();
		}
	}
	
    /**
     * Get status of compass sensor.
     * 
     * @return			status
     */
	public int getStatus() {
		return this.status;
	}
	
	/**
	 * Get the most recent compass heading.
	 * 
	 * @return			heading
	 */
	public float getHeading() {
        this.lastAccessTime = System.currentTimeMillis();
		return this.heading;
	}
	
	/**
	 * Set the timeout to turn off compass sensor if getHeading() hasn't been called.
	 * 
	 * @param timeout		Timeout in msec.
	 */
	public void setTimeout(long timeout) {
		this.TIMEOUT = timeout;
	}
	
	/**
	 * Get the timeout to turn off compass sensor if getHeading() hasn't been called.
	 * 
	 * @return timeout in msec
	 */
	public long getTimeout() {
		return this.TIMEOUT;
	}
}
