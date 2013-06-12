package bwr.camerashade;

import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.app.Activity;
import android.content.Context;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
	
	private EdgeDetectionRenderer renderer;
	private WakeLock wakelock;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// Get the entire screen
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		renderer = new EdgeDetectionRenderer(this);
		
		setContentView(renderer);
		
		wakelock = ((PowerManager)getSystemService ( Context.POWER_SERVICE )).newWakeLock(PowerManager.FULL_WAKE_LOCK, "WakeLock");
	    wakelock.acquire();
	}

	@Override
	protected void onResume() {
		super.onResume();
		renderer.onResume();
		wakelock.acquire();
	}
	
	@Override
	protected void onPause() {
	    if ( wakelock.isHeld() )
	        wakelock.release();
		super.onPause();
		renderer.onPause();
	}

}

