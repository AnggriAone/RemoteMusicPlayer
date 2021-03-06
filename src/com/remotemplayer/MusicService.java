/**
 * 
 */
package com.remotemplayer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * @author Sapan
 */
public class MusicService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, OnCompletionListener {

	private static final String ACTION_PLAY = "PLAY";
	private static final String APP_TEMP_PATH = "/sdcard/remotemplayer/";
	private static final String MUSIC_PATH_FILE = "musicPath.txt";
	private static final String PLAYLIST_PATH_FILE = "playlistPath.txt";
	private static String playlistPath = "/sdcard/My SugarSync Folders/Uploaded by Email/";
	private static String musicPath = "/sdcard/music/";
	private static String newsFlashPath = "/sdcard/newsflash/";

	private static String mUrl;

	private static PlaylistObserver playlistObserver;
	private static NewsFlashObserver newsFlashObserver;

	private static MusicService mInstance = null;
	private boolean batteryInfoReceiverEventReceived = false;
	private boolean isCharging = false;

	private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent intent) {
			int isPlugged = intent.getIntExtra("plugged", 0);
			// mBatInfoReceiver.setResultExtras(intent.getExtras());
			batteryInfoReceiverEventReceived = true;
			if (isPlugged != 0) {
				isCharging = true;
			} else {
				isCharging = false;
			}
			if (getInstance() != null && mMediaPlayer != null) {
				if (isPlugged != 0) {
					isCharging = true;
					if (mState == State.Stopped) {
						MusicService.getInstance().playSong(currentSongIndex);
						return;
					}
					MusicService.getInstance().startMusic();
				} else {
					isCharging = false;
					MusicService.getInstance().pauseMusic();
				}
			}
			Log.i("MusicService", "BatteryInfoReceiver " + isPlugged);

		}
	};

	@Override
	public void onCreate() {
		mInstance = this;

		// mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

	}

	public class LocalBinder extends Binder {
		MusicService getService() {
			return MusicService.this;
		}
	}

	// The Media Player
	MediaPlayer mMediaPlayer = null;

	private final IBinder mBinder = new LocalBinder();

	// indicates the state our service:
	enum State {
		Retrieving, // the MediaRetriever is retrieving music
		Stopped, // media player is stopped and not prepared to play
		Preparing, // media player is preparing...
		Playing, // playback active (media player ready!). (but the media player may actually be
					// paused in this state if we don't have audio focus. But we stay in this state
					// so that we know we have to resume playback once we get focus back)
		Paused
		// playback paused (media player ready!)
	};

	State mState = State.Retrieving;
	private int mBufferPosition;
	private List<String> playlist;
	private int currentSongIndex;
	private boolean batteryInfoReceiverRegistered = false;
	private boolean useNewsFlashPath = false;
	private int newsFlashIndex = -1;
	private static String mSongTitle;
	private static String mSongPicUrl;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// if (intent.getAction().equals(ACTION_PLAY)) {
		if (!batteryInfoReceiverRegistered) {
			this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			batteryInfoReceiverRegistered = true;
		}

		mMediaPlayer = new MediaPlayer(); // initialize it here
		mMediaPlayer.setOnPreparedListener(this);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);
		// mMediaPlayer.setOnBufferingUpdateListener(this);
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		// initMediaPlayer();
		// }

		// ##### Read the file back in #####
		Log.i("MusicService", "Trying to read playlist path from file");
		try {
			if (new File(APP_TEMP_PATH + PLAYLIST_PATH_FILE).exists()) {
				FileInputStream fIn = new FileInputStream(new File(APP_TEMP_PATH + PLAYLIST_PATH_FILE));
				InputStreamReader inputreader = new InputStreamReader(fIn);
				BufferedReader in = new BufferedReader(inputreader);
				String line = in.readLine();
				Log.i("MusicService", line);
				setPlaylistPath(line);
			}
		} catch (IOException ioe) {
			Log.e("MusicService", "Unable to read playlist path from file", ioe);
		}

		// ##### Read the file back in #####
		Log.i("MusicService", "Trying to read music path from file");
		try {
			if (new File(APP_TEMP_PATH + MUSIC_PATH_FILE).exists()) {
				FileInputStream fIn = new FileInputStream(new File(APP_TEMP_PATH + MUSIC_PATH_FILE));
				InputStreamReader inputreader = new InputStreamReader(fIn);
				BufferedReader in = new BufferedReader(inputreader);
				String line = in.readLine();
				Log.i("MusicService", line);
				setMusicPath(line);
			}
		} catch (IOException ioe) {
			Log.e("MusicService", "Unable to read music path from file", ioe);
		}

		playlistObserver = new PlaylistObserver(playlistPath);
		playlistObserver.setService(this);
		playlistObserver.startWatching();
		Log.i("MusicService", "Started watching for file events at " + playlistPath);

		newsFlashObserver = new NewsFlashObserver(newsFlashPath);
		newsFlashObserver.setService(this);
		newsFlashObserver.startWatching();
		Log.i("MusicService", "Started watching for file events at " + newsFlashPath);

		Log.i("MusicService", "Now starting to read the initial playlist file");
		playlistObserver.readPlaylist("playlist.txt");

		return START_STICKY;
	}

	private void initMediaPlayer() {
		try {
			mMediaPlayer.setDataSource(mUrl);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Workaround for bug: http://code.google.com/p/android/issues/detail?id=957
			mMediaPlayer.reset();
			try {
				mMediaPlayer.setDataSource(mUrl);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			mMediaPlayer.prepareAsync(); // prepare async to not block main thread
		} catch (IllegalStateException e) {
			// TODO Workaround for bug: http://code.google.com/p/android/issues/detail?id=957
			mMediaPlayer.reset();
			try {
				mMediaPlayer.setDataSource(mUrl);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			mMediaPlayer.prepareAsync();
		}
		mState = State.Preparing;
	}

	public void restartMusic() {
		mState = State.Retrieving;
		mMediaPlayer.reset();
		initMediaPlayer();
	}

	protected void setBufferPosition(int progress) {
		mBufferPosition = progress;
	}

	/** Called when MediaPlayer is ready */
	@Override
	public void onPrepared(MediaPlayer player) {
		mState = State.Playing;
		mMediaPlayer.start();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onDestroy() {
		if (mMediaPlayer != null) {
			mMediaPlayer.release();
		}
		mState = State.Retrieving;
	}

	public MediaPlayer getMediaPlayer() {
		return mMediaPlayer;
	}

	public void pauseMusic() {
		if (mState.equals(State.Playing)) {
			mMediaPlayer.pause();
			mState = State.Paused;
		}
	}

	public void startMusic() {
		if (!mState.equals(State.Preparing) && !mState.equals(State.Retrieving) && !mState.equals(State.Playing)) {
			mMediaPlayer.start();
			mState = State.Playing;
		}
	}

	public boolean isPlaying() {
		if (mState.equals(State.Playing)) {
			return true;
		}
		return false;
	}

	public void setPlaylist(List<String> playlist) {
		Log.i("MusicService", "Successfuly updated playlist");
		this.playlist = playlist;
		startPlayingFromPlayList();
	}

	private void startPlayingFromPlayList() {
		if (playlist.size() == 0) {
			Log.w("MusicService", "No song in playlist");
			return;
		}
		currentSongIndex = 0;
		playSong(currentSongIndex);
	}

	private void playSong(int currentSongIndex) {

		if (batteryInfoReceiverEventReceived) {

			// Bundle m = mBatInfoReceiver.getResultExtras(true);
			// Don't play song if charger is not plugged in
			// if (mBatInfoReceiver.getResultExtras(true).getInt("plugged") == 0) {
			if (!isCharging) {
				Log.w("MusicService", "Battery not plugged in. Stopping Mediaplayer");
				return;
			}
		}

		mMediaPlayer.reset();

		// Restart playlist from beginning
		if (currentSongIndex == playlist.size()) {
			this.currentSongIndex = 0;
			currentSongIndex = 0;
		}

		if (currentSongIndex < playlist.size()) {
			if (newsFlashIndex == currentSongIndex || useNewsFlashPath && playlist.get(currentSongIndex).toLowerCase().contains("newsflash")) {
				mUrl = this.newsFlashPath + playlist.get(currentSongIndex);
				useNewsFlashPath = false;
			} else {
				mUrl = this.musicPath + playlist.get(currentSongIndex);
				Log.i("MusicService", "Trying to play song: " + mUrl);
			}

			File file = new File(mUrl);
			if (file.exists()) {
				Log.i("MusicService", "Now Playing: " + mUrl);
				initMediaPlayer();
			} else {
				Log.w("MusicService", mUrl + " doesn't exist");
				this.currentSongIndex++;
				playSong(this.currentSongIndex);
			}
		}
	}

	// public int getMusicDuration() {
	// if (!mState.equals(State.Preparing) && !mState.equals(State.Retrieving)) {
	// return mMediaPlayer.getDuration();
	// }
	// return 0;
	// }

	public int getCurrentPosition() {
		if (!mState.equals(State.Preparing) && !mState.equals(State.Retrieving)) {
			return mMediaPlayer.getCurrentPosition();
		}
		return 0;
	}

	public int getBufferPercentage() {
		// if (mState.equals(State.Preparing)) {
		return mBufferPosition;
		// }
		// return getMusicDuration();
	}

	public void seekMusicTo(int pos) {
		if (mState.equals(State.Playing) || mState.equals(State.Paused)) {
			mMediaPlayer.seekTo(pos);
		}
	}

	public static MusicService getInstance() {
		return mInstance;
	}

	public static void setSong(String url, String title, String songPicUrl) {
		mUrl = url;
		mSongTitle = title;
		mSongPicUrl = songPicUrl;
	}

	public String getSongTitle() {
		return mSongTitle;
	}

	public String getSongPicUrl() {
		return mSongPicUrl;
	}

	// @Override
	// public void onBufferingUpdate(MediaPlayer mp, int percent) {
	// setBufferPosition(percent * getMusicDuration() / 100);
	// }

	@Override
	public void onCompletion(MediaPlayer mp) {
		currentSongIndex++;
		playSong(currentSongIndex);
	}

	public static String getPlaylistPath() {
		return playlistPath;
	}

	public static void setPlaylistPath(String playlistPath) {
		MusicService.playlistPath = playlistPath;

		File musicPathFile = new File(APP_TEMP_PATH);
		if (!musicPathFile.exists()) {

			Log.i("MusicService", "Attempting to create dirs " + APP_TEMP_PATH);

			if (musicPathFile.mkdirs()) {
				Log.i("MusicService", "Created dirs " + APP_TEMP_PATH);
			}
		}

		try {
			FileOutputStream fOut = new FileOutputStream(APP_TEMP_PATH + PLAYLIST_PATH_FILE);
			OutputStreamWriter osw = new OutputStreamWriter(fOut);
			BufferedWriter writer = new BufferedWriter(osw);
			writer.write(playlistPath);
			writer.close();
			// osw.write(musicPath);
			// osw.flush();
			// osw.close();
			Log.i("MusicService", "Playlist path File created at " + APP_TEMP_PATH + PLAYLIST_PATH_FILE);
		} catch (IOException e) {
			Log.e("MusicService", "Cannot create playlist path file", e);
		}

		playlistObserver = new PlaylistObserver(playlistPath);
		playlistObserver.setService(getInstance());
		playlistObserver.startWatching();
		Log.i("MusicService", "Started watching for file events at " + playlistPath);

		Log.i("MusicService", "Now starting to read the changed playlist file");
		playlistObserver.readPlaylist("playlist.txt");
	}

	public static String getMusicPath() {
		return musicPath;
	}

	public void setMusicPath(String musicPath) {
		if (!musicPath.endsWith(File.separator)) {
			musicPath.concat(File.separator);
		}

		File musicPathFile = new File(APP_TEMP_PATH);
		if (!musicPathFile.exists()) {

			Log.i("MusicService", "Attempting to create dirs " + APP_TEMP_PATH);

			if (musicPathFile.mkdirs()) {
				Log.i("MusicService", "Created dirs " + APP_TEMP_PATH);
			}
		}

		try {
			FileOutputStream fOut = new FileOutputStream(APP_TEMP_PATH + MUSIC_PATH_FILE);
			OutputStreamWriter osw = new OutputStreamWriter(fOut);
			BufferedWriter writer = new BufferedWriter(osw);
			writer.write(musicPath);
			writer.close();
			// osw.write(musicPath);
			// osw.flush();
			// osw.close();
			Log.i("MusicService", "Music File created at " + APP_TEMP_PATH + MUSIC_PATH_FILE);
		} catch (IOException e) {
			Log.e("MusicService", "Cannot create music path file", e);
		}

		MusicService.musicPath = musicPath;
		Log.i("MusicService", "Music Path changed to " + musicPath);
		// if (getInstance().mState != State.Playing && getInstance().mState != State.Paused && getInstance().mState !=
		// State.Preparing) {
		// Attempt to read the playlist again
		if (playlistObserver == null) {
			setPlaylistPath(playlistPath);
		} else {
			playlistObserver.readPlaylist("playlist.txt");
		}
		// }
	}

	public void setNextNewsFlash(String path) {
		Log.i("MusicService", "New news flash found: " + path);
		for (int i = currentSongIndex + 1; i < playlist.size(); ++i) {
			if (playlist.get(i).toLowerCase().contains("newsflash")) {
				Log.i("MusicService", "Replacing newsflash #" + i + ": " + playlist.get(i) + " with new news flash: " + path);
				newsFlashIndex = i;
				playlist.set(i, path);
				useNewsFlashPath = true;
				break;
			}
		}
	}

	public String getNewsFlashPath() {
		return newsFlashPath;
	}

	public void setNewsFlashPath(String newsFlashPath) {
		this.newsFlashPath = newsFlashPath;
	}
}
