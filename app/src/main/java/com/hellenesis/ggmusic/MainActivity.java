package com.hellenesis.ggmusic;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewDebug;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private MusicService mService;
    private boolean mBound = false;
    private static final int UPDATE_PROGRESS = 1;
    private ProgressBar pbProgress;

    private MusicReceiver musicReceiver;

    public static final String ACTION_MUSIC_START =
            "com.hellenesis.ggmusic.ACTION_MUSIC_START";
    public static final String ACTION_MUSIC_STOP =
            "com.hellenesis.ggmusic.ACTION_MUSIC_STOP";

    public static final String TITLE = "com.hellenesis.ggmusic.TITLE";
    public static final String ARTIST = "com.hellenesis.ggmusic.ARTIST";
    public static final String DATA_URI = "com.hellenesis.ggmusic.DATA_URI";
    private MediaPlayer mMediaPlayer = null;
    private BottomNavigationView navigation;
    private TextView tvBottomTitle;
    private TextView tvBottomArtist;
    private ImageView ivAlbumThumbnail;
    private ImageView ivPlay;
    private boolean mPlaystatus;
   //Intent serviceIntent;

    private ContentResolver mContentResolver;
    private ListView mPlaylist;
    private MediaCursorAdapter mCursorAdapter;

    private final String SELECTION =
            MediaStore.Audio.Media.IS_MUSIC + " =? " + " AND "
                    + MediaStore.Audio.Media.MIME_TYPE + " LIKE? ";
    private final String[] SELECTION_ARGS = {
            Integer.toString(1),
            "audio/mpeg"
    };
    private final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder iBinder){
            MusicService.MusicServiceBinder binder = (MusicService.MusicServiceBinder) iBinder;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            mBound = false;
        }
    };
    private ListView.OnItemClickListener itemClickListener
            = new ListView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            Cursor cursor = mCursorAdapter.getCursor();

            //mPlaystatus = true;
            //ivPlay.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
            if (cursor != null && cursor.moveToPosition(i)) {
                int titleIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.TITLE
                );
                int artistIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.ARTIST
                );
                int albumIdIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.ALBUM_ID
                );
                int dataIndex = cursor.getColumnIndex(
                        MediaStore.Audio.Media.DATA
                );

                String title = cursor.getString(titleIndex);
                String artist = cursor.getString(artistIndex);
                Long albumId = cursor.getLong(albumIdIndex);
                String data = cursor.getString(dataIndex);

                // Uri dataUri = Uri.parse(data);
                Intent serviceIntent = new Intent(MainActivity.this, MusicService.class);
                serviceIntent.putExtra(MainActivity.DATA_URI, data);
                serviceIntent.putExtra(MainActivity.TITLE, title);
                serviceIntent.putExtra(MainActivity.ARTIST, artist);
                //startService(serviceIntent);
                //startService(serviceIntent);
                ivPlay.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    startForegroundService(serviceIntent);
                }else {
                    startService(serviceIntent);
                }
                /*if (mMediaPlayer != null) {
                    try {
                        mMediaPlayer.reset();
                        mMediaPlayer.setDataSource(
                                MainActivity.this, dataUri
                        );
                        mMediaPlayer.prepare();
                        mMediaPlayer.start();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }*/
                navigation.setVisibility(View.VISIBLE);

                if (tvBottomTitle != null) {
                    tvBottomTitle.setText(title);
                }
                if (tvBottomArtist != null) {
                    tvBottomArtist.setText(artist);
                }
                Uri albumUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
                );
                Cursor albumCursor = mContentResolver.query(
                        albumUri,
                        null,
                        null,
                        null,
                        null
                );
                if (albumCursor != null && albumCursor.getCount() > 0) {
                    albumCursor.moveToFirst();
                    int albumArtIndex = albumCursor.getColumnIndex(
                            MediaStore.Audio.Albums.ALBUM_ART
                    );
                    String albumArt = albumCursor.getString(
                            albumArtIndex
                    );
                    Glide.with(MainActivity.this).load(albumArt).into(ivAlbumThumbnail);
                    albumCursor.close();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContentResolver = getContentResolver();
        mCursorAdapter = new MediaCursorAdapter(MainActivity.this);
        mPlaylist = findViewById(R.id.lv_playlist);
        mPlaylist.setAdapter(mCursorAdapter);
        mPlaylist.setOnItemClickListener(itemClickListener);

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    MainActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
            } else {
                requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } else {
            initPlaylist();
        }
        navigation = findViewById(R.id.navigation);
        LayoutInflater.from(MainActivity.this)
                .inflate(R.layout.bottom_media_toolbar, navigation, true);
        pbProgress = navigation.findViewById(R.id.progress);
        ivPlay = navigation.findViewById(R.id.iv_play);
        tvBottomTitle = navigation.findViewById(R.id.tv_bottom_title);
        tvBottomArtist = navigation.findViewById(R.id.tv_bottom_artist);
        ivAlbumThumbnail = navigation.findViewById(R.id.iv_thumbnail);


        if (ivPlay != null) {
            ivPlay.setOnClickListener(MainActivity.this);
        }
        navigation.setVisibility(View.GONE);

        /*if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
        }*/
        //navigation = findViewById(R.id.navigation);

     //动态注册广播
        musicReceiver = new MusicReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_MUSIC_START);
        intentFilter.addAction(ACTION_MUSIC_STOP);
        registerReceiver(musicReceiver,intentFilter);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initPlaylist();
                }
                break;
            default:
                break;
        }
    }

    private void initPlaylist() {
        Cursor cursor = mContentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                SELECTION,
                SELECTION_ARGS,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER
        );
        mCursorAdapter.swapCursor(cursor);
        mCursorAdapter.notifyDataSetChanged();

    }

    @Override
    protected void onStart() {
        super.onStart();
        /*if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
        }*/
        Intent intent = new Intent(MainActivity.this,MusicService.class);
        bindService(intent,mConn ,Context.BIND_AUTO_CREATE);
    }
    @Override
    protected void onStop() {
        unbindService(mConn);
        mBound = false;

        /*if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            //Log.d(TAG,"onStop invoked!");
        }*/

        super.onStop();
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.iv_play){
            //mPlaystatus = !mPlaystatus;
            if(mService.isPlaying() == true){
                mService.pause();
                ivPlay.setImageResource(R.drawable.ic_pause_circle_outline_black_24dp);
            }else {
                mService.play();
                ivPlay.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
            }
        }
       /* switch (view.getId()) {
            case R.id.iv_play:
                mPlaystatus = !mPlaystatus;
                if (mPlaystatus == true) {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.start();
                    }
                    ivPlay.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
                } else {
                    if (mMediaPlayer != null) {
                        mMediaPlayer.pause();
                    }
                    ivPlay.setImageResource(R.drawable.ic_pause_circle_outline_black_24dp);
                }
                break;
            default:
                break;
        }*/
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()){
        public void handleMessage(Message msg){
            switch (msg.what){
                case UPDATE_PROGRESS:
                    int position = msg.arg1;
                    pbProgress.setProgress(position);
                    break;
                default:
                    break;
            }
        }
    };

    private class MusicProgressRunnable implements Runnable {
        public MusicProgressRunnable() {
        }

        @Override
        public void run() {
            boolean mThreadWorking = true;
            while (mThreadWorking) {
                try {
                    if (mService != null){
                        int position =
                                mService.getCurrentPosition();
                        Message message = new Message();
                        message.what = UPDATE_PROGRESS;
                        message.arg1 = position;
                        mHandler.sendMessage(message);
                    }
                    mThreadWorking = mService.isPlaying();
                    Thread.sleep(100);
                }catch (InterruptedException ie){
                    ie.printStackTrace();
                }
            }
        }
    }
    @Override
    protected void onDestroy(){
        unregisterReceiver(musicReceiver);
        super.onDestroy();
    }
   //子线程
    public class MusicReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context,Intent intent){
            if(mService != null){
                pbProgress.setMax(mService.getDuration());
                new Thread(new MusicProgressRunnable()).start();
            }
        }
    }
}
