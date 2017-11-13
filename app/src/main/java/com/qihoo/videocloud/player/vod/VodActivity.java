
package com.qihoo.videocloud.player.vod;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.qihoo.livecloud.play.GifRecordConfig;
import com.qihoo.livecloud.play.callback.PlayerCallback;
import com.qihoo.livecloud.plugin.ILiveCloudPlugin;
import com.qihoo.livecloud.sdk.QHVCSdk;
import com.qihoo.livecloud.tools.Logger;
import com.qihoo.livecloud.tools.NetUtil;
import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.IQHVCPlayer;
import com.qihoo.videocloud.IQHVCPlayerAdvanced;
import com.qihoo.videocloud.QHVCPlayer;
import com.qihoo.videocloud.QHVCPlayerPlugin;
import com.qihoo.videocloud.player.LogAdapter;
import com.qihoo.videocloud.player.PlayConstant;
import com.qihoo.videocloud.utils.AndroidUtil;
import com.qihoo.videocloud.utils.NoDoubleClickListener;
import com.qihoo.videocloud.view.QHVCTextureView;
import com.qihoo.videocloud.widget.ViewHeader;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qihoo.videocloud.player.PlayConstant.SHOW_MODEL_LAND;
import static com.qihoo.videocloud.player.PlayConstant.SHOW_MODEL_PORT;
import static com.qihoo.videocloud.player.PlayConstant.SHOW_MODEL_PORT_SMALL;

public class VodActivity extends Activity {

    private static final String TAG = VodActivity.class.getSimpleName();
    private static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = 1000;

    @PlayConstant.ShowModel
    int currentShowModel = SHOW_MODEL_PORT_SMALL;

    private IQHVCPlayerAdvanced qhvcPlayer;
    private String url;
    private String channelId;
    private String businessId;
    private boolean autoDecoded;

    private QHVCTextureView playView;
    private RelativeLayout rlPlayerContainer;
    private ViewHeader viewHeaderMine;
    private ImageView btnPlay;
    private TextView tvPlayTime;

    private SeekBar sbProgress;
    private int currentProgress;

    private TextView tvDuration;
    private View ivZoom;
    private ListView lvLog;
    private LogAdapter logAdapter;
    private List<String> logList = new ArrayList<>();

    private int videoWidth;
    private int videoHeight;

    private Map<String, Object> mediaInformationMap;
    private long downloadBitratePerSecond;//下行码率
    private long videoBitratePerSecond;// 视频码率
    private long videoFrameRatePerSecond;//视频帧率

    private long mBeginTick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemNavigationBar();
        super.onCreate(savedInstanceState);

        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_vod);
        checkSelfPermissionAndRequest(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE);

        initView();
        initData();
        vodProxy();
    }

    private void initData() {
        Intent i = getIntent();
        businessId = i.getStringExtra("businessId");
        if (!TextUtils.isEmpty(businessId)) {
            QHVCSdk.getInstance().getConfig().setBusinessId(businessId);
        }

        channelId = i.getStringExtra("channelId");
        url = i.getStringExtra("url");
        autoDecoded = i.getBooleanExtra("autoDecoded", Boolean.FALSE);
    }

    private void initView() {
        rlPlayerContainer = (RelativeLayout) findViewById(R.id.rl_player_container);
        playView = (QHVCTextureView) findViewById(R.id.playView);
        viewHeaderMine = (ViewHeader) findViewById(R.id.viewHeaderMine);
        viewHeaderMine.setLeftText("点播");
        viewHeaderMine.getLeftIcon().setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View v) {
                playerClose();
                finish();
            }
        });

        lvLog = (ListView) findViewById(R.id.lv_log);
        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            logAdapter = new LogAdapter(this, logList, R.color.white);
        } else {
            logAdapter = new LogAdapter(this, logList, R.color.color_666666);
        }
        lvLog.setAdapter(logAdapter);

        btnPlay = (ImageView) findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (qhvcPlayer != null && qhvcPlayer.isPlaying()) {
                    qhvcPlayer.pause();

                    btnPlay.setImageDrawable(null);
                    btnPlay.setImageDrawable(getResources().getDrawable(R.drawable.play));
                } else if (qhvcPlayer != null && qhvcPlayer.isPaused()) {
                    qhvcPlayer.start();

                    btnPlay.setImageDrawable(null);
                    btnPlay.setImageDrawable(getResources().getDrawable(R.drawable.pause));
                }
            }
        });

        tvPlayTime = (TextView) findViewById(R.id.tv_play_time);
        sbProgress = (SeekBar) findViewById(R.id.sb_progress);
        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentProgress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                currentProgress = 0;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                if (qhvcPlayer != null) {

                    qhvcPlayer.seekTo((qhvcPlayer.getDuration() * currentProgress) / 100, false);
                }
            }
        });

        tvDuration = (TextView) findViewById(R.id.tv_duration);

        ivZoom = findViewById(R.id.iv_zoom);
        ivZoom.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View v) {
                if (currentShowModel == SHOW_MODEL_PORT_SMALL) {

                    if (videoWidth != 0 && videoHeight != 0) {

                        Log.v(TAG, "width: " + videoWidth + " height: " + videoHeight);
                        if (videoHeight > videoWidth) {

                            currentShowModel = SHOW_MODEL_PORT;
                            portZoomIn();
                        } else {

                            currentShowModel = SHOW_MODEL_LAND;
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        }
                    } else {

                        Toast.makeText(VodActivity.this, "cannot zoom. width: " + videoWidth + " height: " + videoHeight, Toast.LENGTH_SHORT).show();
                    }
                } else if (currentShowModel == SHOW_MODEL_PORT) {

                    currentShowModel = SHOW_MODEL_PORT_SMALL;
                    portZoomOut();
                } else if (currentShowModel == SHOW_MODEL_LAND) {

                    currentShowModel = SHOW_MODEL_PORT_SMALL;
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                // SHOW_MODEL_PORT have not zoom
            }
        });
    }

    private void vodProxy() {
        final QHVCPlayerPlugin qhvcPlayerPlugin = QHVCPlayerPlugin.getInstance();

        //若第三方未将播放器需要的so文件捆包，必须设置setDefaultPluginInstalled(false)
        qhvcPlayerPlugin.setDefaultPluginInstalled(true);

        if (qhvcPlayerPlugin.isDefaultPluginInstalled()) {
            vod();
        } else if (qhvcPlayerPlugin.isPluginInstalled()) {
            int result = qhvcPlayerPlugin.loadPlugin();
            if (result == QHVCPlayerPlugin.ERROR_SUCCESS) {
                vod();
            } else {
                Toast.makeText(this, "播放器插件加载失败" + "(" + result + ")", Toast.LENGTH_SHORT).show();
            }
        } else {
            qhvcPlayerPlugin.checkInstallOrUpdatePlugin(this, new ILiveCloudPlugin.PluginCallback() {
                @Override
                public void onStart(Context context) {
                    Toast.makeText(context, "开始下载播放器插件", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onProgress(Context context, int progress) {
                    Log.d(TAG, "插件下载进度：" + progress);
                }

                @Override
                public void onComplete(Context context, boolean background, int result) {
                    if (isFinishing()) {
                        return;
                    }

                    if (result == QHVCPlayerPlugin.ERROR_SUCCESS) {
                        result = qhvcPlayerPlugin.loadPlugin();
                    }
                    if (result == QHVCPlayerPlugin.ERROR_SUCCESS && !background) {
                        Toast.makeText(context, "播放器插件加载完成", Toast.LENGTH_SHORT).show();
                        vod();
                    } else {
                        Toast.makeText(context, "播放器插件加载失败" + "(" + result + ")", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancel(Context context) {
                    Toast.makeText(context, "取消下载播放器插件", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void vod() {

        qhvcPlayer = new QHVCPlayer(this);
        playView.onPlay();
        playView.setPlayer(qhvcPlayer);
        qhvcPlayer.setDisplay(playView);

        try {
            Map<String, Object> options = new HashMap<>();
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_MUTE, true);
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_POSITION, 30 * 1000);
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_PLAY_MODE, IQHVCPlayerAdvanced.PLAYMODE_LOWLATENCY);
            //            options.put(IQHVCPlayerAdvanced.KEY_OPTION_RENDER_MODE, IQHVCPlayerAdvanced.RENDER_MODE_FULL);
            if (autoDecoded) {
                options.put(IQHVCPlayerAdvanced.KEY_OPTION_DECODE_MODE, IQHVCPlayerAdvanced.LIVECLOUD_SMART_DECODE_MODE);
            } else {
                options.put(IQHVCPlayerAdvanced.KEY_OPTION_DECODE_MODE, IQHVCPlayerAdvanced.LIVECLOUD_SOFT_DECODE_MODE);
            }
            qhvcPlayer.setDataSource(IQHVCPlayer.PLAYTYPE_VOD, url, channelId, "", options);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(this, "数据源异常", Toast.LENGTH_SHORT).show();
            return;
        }

        qhvcPlayer.setOnPreparedListener(new IQHVCPlayer.OnPreparedListener() {
            @Override
            public void onPrepared() {
                qhvcPlayer.start();
            }
        });
        qhvcPlayer.setOnVideoSizeChangedListener(new IQHVCPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(int handle, int width, int height) {
                videoWidth = width;
                videoHeight = height;
                if (playView != null) {
                    playView.setVideoRatio((float) width / (float) height);
                }
            }
        });
        qhvcPlayer.setOnInfoListener(new IQHVCPlayer.OnInfoListener() {
            @Override
            public void onInfo(int handle, int what, int extra) {
                Logger.w(TAG, "onInfo handle: " + handle + " what: " + what + " extra: " + extra);
                if (what == IQHVCPlayer.INFO_LIVE_PLAY_START) {
                    long endTick = System.currentTimeMillis();
                    Logger.d(TAG, "livecloud first render use tick: " + (endTick - mBeginTick));
                } else if (what == IQHVCPlayer.INFO_DEVICE_RENDER_ERR) {
                    // err
                    if (Logger.LOG_ENABLE) {
                        Logger.e(TAG, "dvrender err");
                    }
                } else if (what == IQHVCPlayer.INFO_DEVICE_RENDER_QUERY_SURFACE) {

                    if (playView != null) {
                        if (qhvcPlayer != null && !qhvcPlayer.isPaused()) {
                            playView.render_proc(PlayerCallback.DEVICE_RENDER_QUERY_SURFACE, 0/*不使用此变量*/);
                        }
                    }
                } else if (what == IQHVCPlayer.INFO_RENDER_RESET_SURFACE) {

                    if (playView != null) {
                        playView.pauseSurface();
                    }
                }
            }
        });
        qhvcPlayer.setOnBufferingEventListener(new IQHVCPlayer.OnBufferingEventListener() {
            @Override
            public void onBufferingStart(int handle) {

                Log.w(TAG, "buffering event. start");
            }

            @Override
            public void onBufferingProgress(int handle, int progress) {
                Log.v(TAG, "buffering event. progress: " + progress);
            }

            @Override
            public void onBufferingStop(int handle) {
                Log.w(TAG, "buffering event. stop " + ((qhvcPlayer != null) ? qhvcPlayer.getCurrentPosition() : 0));

            }
        });
        qhvcPlayer.setOnErrorListener(new IQHVCPlayer.OnErrorListener() {
            @Override
            public boolean onError(int handle, int what, int extra) {
                return false;
            }
        });
        qhvcPlayer.setOnSeekCompleteListener(new IQHVCPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(int handle) {

                Logger.d(TAG, "seek complete");
            }
        });
        qhvcPlayer.setOnCompletionListener(new IQHVCPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(int handle) {

            }
        });
        qhvcPlayer.setOnPlayerNetStatsListener(new IQHVCPlayerAdvanced.OnPlayerNetStatsListener() {
            @Override
            public void onPlayerNetStats(int handle, long dvbps, long dabps, long dvfps, long dafps, long fps, long bitrate, long param1, long param2, long param3) {

                //Log.w(TAG, "dvbps: "  + dvbps + " dabps: " + dabps + " dvfps: " + dvfps + " dafps: " + dafps + " fps: " + fps +" bitrate: " +bitrate);

                downloadBitratePerSecond = dvbps + dabps;
                videoBitratePerSecond = bitrate;
                videoFrameRatePerSecond = fps;
            }
        });
        qhvcPlayer.setOnProgressChangeListener(new IQHVCPlayer.onProgressChangeListener() {
            @Override
            public void onProgressChange(int handle, final int total, final int progress) {
                if (progress != 0) {
                    sbProgress.setProgress(progress * 100 / total);
                } else {
                    sbProgress.setProgress(0);
                }

                tvPlayTime.setText(AndroidUtil.getTimeString(progress));
                tvDuration.setText(AndroidUtil.getTimeString(total));

                showLog();
            }
        });
        qhvcPlayer.setOnBufferingUpdateListener(new IQHVCPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(int handle, int percent) {
                Log.w(TAG, "buffering: " + percent + " volume: " + qhvcPlayer.getVolume());
            }
        });

        try {
            mBeginTick = System.currentTimeMillis();
            qhvcPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(this, "prepareAsync 异常", Toast.LENGTH_SHORT).show();
        }
    }

    // 纵向显示-- 放大
    private void portZoomIn() {
        ViewGroup.LayoutParams layoutParams = rlPlayerContainer.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        rlPlayerContainer.setLayoutParams(layoutParams);
        rlPlayerContainer.postInvalidate();

        ViewGroup.LayoutParams videolayoutParams = playView.getLayoutParams();
        videolayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        videolayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        playView.setLayoutParams(videolayoutParams);
        if (qhvcPlayer != null)
            qhvcPlayer.setDisplay(playView);

        //        lvLog.setPadding(30, 140, 0, 0);
        logAdapter.setTextColorResId(R.color.white);
        logAdapter.notifyDataSetChanged();
    }

    // 纵向显示-- 缩小
    private void portZoomOut() {

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density; // 屏幕密度（像素比例：0.75/1.0/1.5/2.0）
        int densityDPI = dm.densityDpi; // 屏幕密度（每寸像素：120/160/240/320）

        ViewGroup.LayoutParams layoutParams = rlPlayerContainer.getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = (int) (density * 183.3);//ViewGroup.LayoutParams.MATCH_PARENT;
        rlPlayerContainer.setLayoutParams(layoutParams);
        rlPlayerContainer.postInvalidate();

        ViewGroup.LayoutParams videolayoutParams = playView.getLayoutParams();
        videolayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        videolayoutParams.height = (int) (density * 183.3);//ViewGroup.LayoutParams.MATCH_PARENT;
        playView.setLayoutParams(videolayoutParams);
        if (qhvcPlayer != null)
            qhvcPlayer.setDisplay(playView);

        //        lvLog.setPadding(30, 198, 0, 0);
        //        lvLog.setPadding(0, 0, 0, 0);
        logAdapter.setTextColorResId(R.color.color_666666);
        logAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void playerClose() {
        if (qhvcPlayer != null) {
            qhvcPlayer.stop();
            qhvcPlayer.release();
            qhvcPlayer = null;
        }
    }

    private void showLog() {
        if (mediaInformationMap == null || mediaInformationMap.isEmpty()) {
            mediaInformationMap = qhvcPlayer != null ? qhvcPlayer.getMediaInformation() : null;
        }

        logList.clear();

        logList.add("版本号: " + QHVCSdk.getInstance().getVersion());
        logList.add("播放url: " + url);
        logList.add("分辨率: " + videoWidth + "*" + videoHeight);
        logList.add("码率: " + videoBitratePerSecond / 1024 + "k");
        logList.add("帧率: " + videoFrameRatePerSecond);
        if (mediaInformationMap != null && !mediaInformationMap.isEmpty()) {
            logList.add("音频格式: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_AUDIO_FORMAT_STRING));
            logList.add("音频采样率: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_AUDIO_SAMPLE_RATE_INT));
            logList.add("音频轨道: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_AUDIO_CHANNEL_INT));
            logList.add("视频编码格式: " + mediaInformationMap.get(QHVCPlayer.KEY_MEDIA_INFO_VIDEO_FORMAT_STRING));
        } else {
            logList.add("音频格式: ");
            logList.add("音频采样率: ");
            logList.add("音频轨道: ");
            logList.add("视频编码格式: ");
        }
        logList.add("下行流量: " + downloadBitratePerSecond / 1024 + "k");
        logList.add("网络类型: " + NetUtil.getNetWorkTypeToString(this));

        logAdapter.setList(logList);
        logAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        Log.w(TAG, "onConfigurationChanged");
        playView.stopRender();
        setContentView(R.layout.activity_vod);
        initView();
        if (qhvcPlayer != null) {
            playView.onPlay();
            playView.setPlayer(qhvcPlayer);
            qhvcPlayer.setDisplay(playView);
        }
    }

    private void hideSystemNavigationBar() {
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            View view = this.getWindow().getDecorView();
            view.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            View decorView = getWindow().getDecorView();
            //            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            //                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed()");

        playerClose();
        super.onBackPressed();
    }

    private void startRecorder() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String fileName = dateFormat.format(date) + ".gif";
        String filePath = new File(Environment.getExternalStorageDirectory(), fileName).getAbsolutePath();

        if (qhvcPlayer != null) {

            Map<String, Object> map = qhvcPlayer.getMediaInformation();
            Logger.d(TAG, "media infomation: " + map.toString());
            GifRecordConfig config = new GifRecordConfig();
            config.setWidth((int) map.get(IQHVCPlayer.KEY_MEDIA_INFO_VIDEO_WIDTH_INT));
            config.setHeight((int) map.get(IQHVCPlayer.KEY_MEDIA_INFO_VIDEO_HEIGHT_INT));
            config.setOutputFps(5);
            config.setSampleInterval(100);

            int ret = qhvcPlayer.startRecorder(filePath, IQHVCPlayerAdvanced.RECORDER_FORMAT_GIF, config, new IQHVCPlayerAdvanced.OnRecordListener() {
                @Override
                public void onRecordSuccess() {
                    Toast.makeText(VodActivity.this, "record success", Toast.LENGTH_SHORT).show();
                }
            });
            Logger.d(TAG, "start recorder. ret: " + ret);
        }
    }

    private void stopRecorder() {
        if (qhvcPlayer != null) {
            int ret = qhvcPlayer.stopRecorder();
            Logger.d(TAG, "stop recorder. ret: " + ret);
        }
    }

    public boolean checkSelfPermissionAndRequest(String permission, int requestCode) {
        Logger.d(TAG, "checkSelfPermission " + permission + " " + requestCode);
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[] {
                            permission
                    },
                    requestCode);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String permissions[], @NonNull int[] grantResults) {
        Logger.d(TAG, "onRequestPermissionsResult " + requestCode + " " + Arrays.toString(permissions) + " " + Arrays.toString(grantResults));
        switch (requestCode) {
            case PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                }
            }
                break;

            default:
                break;
        }
    }
}
