package com.tudelft.triblerdroid.first;


import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.ppsp.test.R;

public class StatisticsActivity extends Activity{
	protected Integer _seqCompInt;
	private TextView txtDownSpeed = null; 
	private TextView txtUpSpeed = null; 
	private TextView txtLeechers = null;
	private TextView txtSeeders = null;
	protected UpdateTask _updateTask;

	String hash; 
	String tracker;
	String destination;
	long seqcomp;
	int dspeed, uspeed, nleech, nseed;
	String progstr;
	ExecutorService exec;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stats);
		_updateTask = new UpdateTask();
		Bundle extras = getIntent().getExtras();
		hash = extras.getString("com.tudelft.triblerdroid.first.VideoPlayerActivity.hash");
		tracker = extras.getString("com.tudelft.triblerdroid.first.VideoPlayerActivity.tracker");
		destination = extras.getString("com.tudelft.triblerdroid.first.VideoPlayerActivity.destination");
		txtDownSpeed = (TextView) findViewById(R.id.down_speed);
		txtUpSpeed = (TextView) findViewById(R.id.up_speed);
		txtLeechers = (TextView) findViewById(R.id.nbr_leech);
		txtSeeders = (TextView) findViewById(R.id.nbr_seed);
		System.out.println("creating thread pool");
		exec = Executors.newCachedThreadPool();
		System.out.println("starting thread");
		exec.execute(_updateTask);
		System.out.println("create done");
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		exec.shutdown();
		Log.w("SwiftStatsActivity", "*** SHUTDOWN SWIFT STATS ACTIVITY ***");
		_updateTask.stop();
	}
	  
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.layout.stats_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case R.id.menu_hide_stats:
			finish();
		default:
			return super.onOptionsItemSelected(item);
		}
	}    

	
	
	private class UpdateTask implements Runnable {
		private boolean running = true;

		public void run() {
			try {

				NativeLib nativelib = new NativeLib();

				while (running) {
					progstr = nativelib.httpprogress(hash);
					String[] elems = progstr.split("/");
					seqcomp = Long.parseLong(elems[0]);

					_seqCompInt = new Integer((int) (seqcomp / 1024));

					txtDownSpeed = (TextView) findViewById(R.id.down_speed);
					progstr = "";
					progstr = nativelib.stats();
					String[] items = progstr.split("/");
					Log.i("stats", progstr);
					dspeed = Integer.parseInt(items[0]);
					uspeed = Integer.parseInt(items[1]);
					nleech = Integer.parseInt(items[2]);
					nseed = Integer.parseInt(items[3]);
					runOnUiThread(new Runnable() {
						public void run() {
							txtDownSpeed.setText(dspeed + " kb/s");
							txtUpSpeed.setText(uspeed + " kb/s");
							txtLeechers.setText(nleech + " ");
							txtSeeders.setText(nseed + " ");
						}
					});
					Thread.sleep(1000);
				}

			} catch (Exception e) {
				Log.w("Swift stats Activity", "Exception");
				e.printStackTrace();
			}
		}
	

		public void stop(){
			running = false;
		}
	}
}
