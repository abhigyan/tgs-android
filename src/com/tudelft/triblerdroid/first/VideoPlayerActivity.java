//Skeleton example from Alexey Reznichenko

package com.tudelft.triblerdroid.first;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import me.ppsp.test.R;
import se.kth.pymdht.Pymdht;

public class VideoPlayerActivity extends Activity {
	//Anand - begin - added constants to pass parameters to next activity
	private static final String _HASH = "com.tudelft.triblerdroid.first.VideoPlayerActivity.hash";
	private static final String _TRACKER = "com.tudelft.triblerdroid.first.VideoPlayerActivity.tracker";
	private static final String _DESTINATION = "com.tudelft.triblerdroid.first.VideoPlayerActivity.destination";
	//end
	NativeLib nativelib = null;
	protected SwiftMainThread _swiftMainThread;
	protected StatsTask _statsTask;
	private VideoView mVideoView = null;
	protected ProgressDialog progressDialog;
	protected Integer _seqCompInt;

	String hash = null; 
	String tracker;
	String destination;
	boolean inmainloop = false;

    public int PROGRESS_DIALOG = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
		Util.mkdirSDContent();
		Bundle extras = getIntent().getExtras();

		hash = extras.getString("hash");//"280244b5e0f22b167f96c08605ee879b0274ce22"
		tracker = "192.16.127.98:20050"; //TODO
		destination = "/sdcard/swift/video.ts";
		if (hash == null){
			return;
		}
		Log.w("final hash", hash);
		startDHT();
		// Start the background process
		_swiftMainThread = new SwiftMainThread();
		_swiftMainThread.start();
		// Show P2P info (stats) according to preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("pref_stats", true)){
			ShowStatistics();
		}
		// start the progress bar
		showDialog(PROGRESS_DIALOG);
		_statsTask = new StatsTask();
		_statsTask.execute( hash, tracker, destination );
		Log.w("video player", "setup DONE");
		startVideoPlayback();
	}
	
	protected Dialog onCreateDialog(int id) {
		if (id == PROGRESS_DIALOG){
			progressDialog = new ProgressDialog(VideoPlayerActivity.this);
			progressDialog.setCancelable(true);
			progressDialog.setMessage("Connectivity: "+Util.getConnectivity(getApplicationContext())+"\nBuffering...");
			// set the progress to be horizontal
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			// reset the bar to the default value of 0
			progressDialog.setProgress(0);

			//stop the engine if the procress scree is cancelled
			progressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					//				_text.setText("TODO HTTPGW engine stopped!");
					// Arno, 2012-01-30: TODO tell HTTPGW to stop serving data
					//nativelib.stop();
					// Raul, 2012-03-27: don't stay here with a black screen. 
					// Go back to video list
					finish();
				}
			});
			return progressDialog;
		}
		return null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.layout.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{

		switch (item.getItemId())
		{
		case R.id.menu_stats:
			ShowStatistics();
			return true;
		case R.id.menu_settings:
			Intent intent = new Intent(getBaseContext(), Preferences.class);
			startActivity(intent);
			return true;
		case R.id.menu_about:
			setContentView(R.layout.about);
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}    

	//stops the Async task when we press back button on video player
	@Override
	public void onStop()
	{
		super.onStop();
		_statsTask.cancel(true);
	}
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		Log.w("SwiftStats", "*** SHUTDOWN SWIFT ***");
		// Raul, 2012-04-25: Halts swift completely on destroy
		_statsTask.cancel(true);
		Log.w("SwiftStats", "*** SHUTDOWN SWIFT ***");
		// Halts swift completely
		//nativelib.stop(); Raul: this raises an exception.
		//I think it's because there is not time to execute it onDestroy
	}

	protected void startDHT(){
		BufferedReader unstable = new BufferedReader(new InputStreamReader(this.getResources().openRawResource(R.raw.bootstrap_unstable)));
		BufferedReader stable = new BufferedReader(new InputStreamReader(this.getResources().openRawResource(R.raw.bootstrap_stable)));
		final Pymdht dht = new Pymdht(9999, unstable, stable);
		Runnable runnable_dht = new Runnable(){
			@Override
			public void run() {
				dht.start();
			}
		};
		Thread dht_thread = new Thread(runnable_dht);
		dht_thread.start();
	}
	
	//starts the video playback
	private void startVideoPlayback() {
		runOnUiThread(new Runnable(){ //Raul, 120920: Why??
			public void run() {
				getWindow().setFormat(PixelFormat.TRANSLUCENT);
				mVideoView = (VideoView) findViewById(R.id.surface_view);
				// Download *and* play, using HTTPGW
				String urlstr = "http://127.0.0.1:8082/"+hash;
				mVideoView.setVideoURI(Uri.parse(urlstr));
				mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
					@Override
					public void onPrepared (MediaPlayer mp) {
						dismissDialog(PROGRESS_DIALOG);
						//Cancel _statsTask if you don't want to get downloading report on catlog 
						//_statsTask.cancel(true);
					}
				});
				mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
						// TODO set as default / post tweet
//						finish();
					}
				});
				MediaController mediaController = new MediaController(VideoPlayerActivity.this);
				mediaController.setAnchorView(mVideoView);
				mVideoView.setMediaController(mediaController);
				mVideoView.start();
				mVideoView.requestFocus();
				//mediaController.show(0); // keep visible
			}
		});
	}

	private class SwiftMainThread extends Thread{
		public void run(){
			try{
				NativeLib nativelib =  new NativeLib();
				String ret = nativelib.start(hash, tracker, destination);
				//startVideoPlayback(); //Raul, 120920: moved to onCreate
				// Arno: Never returns, calls libevent2 mainloop
				if (!inmainloop){
					inmainloop = true;
					Log.w("Swift","Entering libevent2 mainloop");
					int progr = nativelib.mainloop();
					Log.w("Swift","LEFT MAINLOOP!");
				}
			}
			catch (Exception e ){
				e.printStackTrace();
			}
		}
	}


	/**
	 * sub-class of AsyncTask. Retrieves stats from Swift via JNI and
	 * updates the progress dialog.
	 */
	private class StatsTask extends AsyncTask<String, Integer, String> {

		protected String doInBackground(String... args) {

			String ret = "hello";
			if (args.length != 3) {
				ret = "Received wrong number of parameters during initialization!";
			}
			else {
				try {//TODO: catch InterruptedException (onDestroy)

					NativeLib nativelib =  new NativeLib();
					mVideoView = (VideoView) findViewById(R.id.surface_view);
					boolean play = false, pause=false;

					while(true) {
						String progstr = nativelib.httpprogress(args[0]);
						String[] elems = progstr.split("/");
						long seqcomp = Long.parseLong(elems[0]);
						long asize = Long.parseLong(elems[1]);

						if (asize == 0)
							progressDialog.setMax(1024);
						else
							progressDialog.setMax((int)(asize/1024));

						_seqCompInt = new Integer((int)(seqcomp/1024));

						Log.w("SwiftStats", "SeqComp   " + seqcomp );
						if(isCancelled())
							break;

						runOnUiThread(new Runnable(){
							public void run() {
								progressDialog.setProgress(_seqCompInt.intValue() );

							}
						});
						//Raul, 20120425: removed break which caused playback interruption when
						//(asize > 0 && seqcomp == asize) (e.i, file downloaded)
						try{
							Thread.sleep( 1000 );
						}
						catch (InterruptedException e){
							System.out.println(">>>>>>>>>>>>>>>>>>>>>>>Sleep interrupted<<<<<<<<<<<<<<<<<<<<<<<");
						}
					}

				}
				catch (Exception e ) {
					//System.out.println("Stacktrace "+e.toString());
					e.printStackTrace();
					ret = "error occurred during initialization!";
				}
			}
			return ret;
		}
	}

	public void ShowStatistics(){
		Intent intent = new Intent(getBaseContext(), StatisticsActivity.class);
		intent.putExtra(_HASH, hash);
		intent.putExtra(_TRACKER, tracker);
		intent.putExtra(_DESTINATION, destination);
		startActivity(intent);

	}
}
