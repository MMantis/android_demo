
package com.qihoo.videocloud.localserver.player;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.qihoo.livecloud.play.callback.PlayerCallback;
import com.qihoo.livecloud.tools.Logger;
import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.IQHVCPlayer;
import com.qihoo.videocloud.IQHVCPlayerAdvanced;
import com.qihoo.videocloud.QHVCPlayer;
import com.qihoo.videocloud.localserver.VideoItemData;
import com.qihoo.videocloud.localserver.base.BaseLocalServerActivity;
import com.qihoo.videocloud.localserver.download.DownloadManager;
import com.qihoo.videocloud.localserver.download.DownloadTask;
import com.qihoo.videocloud.localserver.download.LocalServerDownloadActivity;
import com.qihoo.videocloud.localserver.setting.LocalServerSettingConfig;
import com.qihoo.videocloud.utils.AndroidUtil;
import com.qihoo.videocloud.utils.NoDoubleClickListener;
import com.qihoo.videocloud.utils.QHVCSharedPreferences;
import com.qihoo.videocloud.utils.Utils;
import com.qihoo.videocloud.view.QHVCTextureView;
import com.qihoo.videocloud.widget.ViewHeader;

import net.qihoo.videocloud.LocalServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocalServerPlayerActivity extends BaseLocalServerActivity {

    public static final String TAG = "LocalServerPlayerActivity";

    protected ImageLoader mImageLoader = ImageLoader.getInstance();
    private boolean mInstanceStateSaved;

    private IQHVCPlayerAdvanced mLiveCloudPlayer;
    private QHVCTextureView mTextureView;

    private ArrayList<VideoItemData> mPlayList;
    private int mCurIndex;
    private int mInitPlayPos = 0;

    private ViewHeader mHeaderView;
    private ViewPager mViewPager;
    private ImageView[] mImageViews;
    private TextView mCurrTimeTextView;
    private TextView mTotalTimeTextView;
    private SeekBar mSeekBar;
    private ImageView mPlayView;
    private ImageView mPauseView;
    private View mHelpView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localserver_player_activity);
        Logger.d(TAG, "onCreate");

        initData(getIntent().getExtras());
        initView();

        startPlay(mCurIndex, mInitPlayPos);
    }

    private void initData(Bundle bundle) {
        List<VideoItemData> playList = (List<VideoItemData>) bundle.getSerializable("list");
        if (playList == null || playList.isEmpty()) {
            throw new IllegalArgumentException("play list is empty");
        }

        mPlayList = new ArrayList<>();
        mPlayList.addAll(playList);
        mCurIndex = bundle.getInt("id");
        mInitPlayPos = bundle.getInt("curPlayPos", 0);
    }

    private void initView() {
        mHeaderView = (ViewHeader) findViewById(R.id.header);
        mHeaderView.getLeftIcon().setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View v) {
                finish();
            }
        });

        mTextureView = (QHVCTextureView) findViewById(R.id.gl2_video_view);

        mSeekBar = (SeekBar) findViewById(R.id.seekbar);
        mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);

        mCurrTimeTextView = (TextView) findViewById(R.id.curr_time);
        mTotalTimeTextView = (TextView) findViewById(R.id.total_time);

        mPlayView = (ImageView) findViewById(R.id.play);
        mPlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mLiveCloudPlayer != null && mLiveCloudPlayer.isPaused()) {
                    mLiveCloudPlayer.start();

                    if (mLiveCloudPlayer.isPlaying()) {
                        mPlayView.setVisibility(View.INVISIBLE);
                        mPauseView.setVisibility(View.VISIBLE);

                        LocalServer.enableCache(true);
                    }
                }
            }
        });
        mPauseView = (ImageView) findViewById(R.id.pause);
        mPauseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mLiveCloudPlayer != null && mLiveCloudPlayer.isPlaying()) {
                    mLiveCloudPlayer.pause();

                    mPlayView.setVisibility(View.VISIBLE);
                    mPauseView.setVisibility(View.INVISIBLE);

                    LocalServer.enableCache(LocalServerSettingConfig.ENABLE_CACHE_WHEN_PAUSE);
                }
            }
        });

        findViewById(R.id.download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                VideoItemData tempVideoItemData = mPlayList.get(mCurIndex);

                String url = tempVideoItemData.getUrl();
                if (TextUtils.isEmpty(url)) {
                    return;
                }

                DownloadTask downloadTask = new DownloadTask();
                downloadTask.rid = tempVideoItemData.getRid();
                downloadTask.url = url;
                downloadTask.file = Utils.getDownloadDir() + downloadTask.rid;
                boolean ret = DownloadManager.getInstance().cachePersistence(downloadTask);
                if (ret) {
                    Intent intent = new Intent(LocalServerPlayerActivity.this, LocalServerDownloadActivity.class);
                    startActivity(intent);
                }

                Toast.makeText(LocalServerPlayerActivity.this, ret ? R.string.str_download_add_success : R.string.str_download_add_failed, Toast.LENGTH_SHORT).show();
            }
        });

        mImageViews = new ImageView[mPlayList.size()];
        for (int i = 0; i < mPlayList.size(); i++) {
            ImageView imageView = new ImageView(this);
            mImageViews[i] = imageView;

            mImageLoader.displayImage(mPlayList.get(i).getImage(), imageView);
        }

        RelativeLayout mRootView = (RelativeLayout) findViewById(R.id.video_layout);
        mViewPager = new ViewPager(this);
        mViewPager.setAdapter(new MyAdapter());
        mRootView.addView(mViewPager);

        mViewPager.setCurrentItem(mCurIndex);
        mViewPager.addOnPageChangeListener(mOnPageChangeListener);

        mHelpView = findViewById(R.id.help_layout);
        mHelpView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHelpView.setVisibility(View.GONE);
            }
        });
        if (QHVCSharedPreferences.getInstence().getBoolean("localserver_player_scroll_help_show", false)) {
            mHelpView.setVisibility(View.GONE);
        } else {
            QHVCSharedPreferences.getInstence().putBooleanValue("localserver_player_scroll_help_show", true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.d(TAG, "onDestroy");

        if (!mInstanceStateSaved) {
            mImageLoader.stop();
        }
        stopPlay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mInstanceStateSaved = true;
    }

    private void startPlay(int id) {
        startPlay(id, 0);
    }

    private void startPlay(int id, int initPlayPos) {
        stopPlay();
        if (mSeekBar != null) {
            mSeekBar.setProgress(0);
            mSeekBar.setSecondaryProgress(0);
        }

        VideoItemData o = mPlayList.get(id);
        String url = LocalServer.getPlayUrl(o.getRid(), o.getUrl());
        String localServerUrl = LocalServer.getPlayUrl(o.getRid(), url);

        Logger.d(TAG, "startPlay index=" + mCurIndex + ", url=" + url + ", localserver_url=" + localServerUrl);

        showCacheInfo(o.getRid(), url);
        LocalServer.enableCache(true);

        mLiveCloudPlayer = new QHVCPlayer(this);
        mTextureView.onPlay();
        mTextureView.setPlayer(mLiveCloudPlayer);
        mLiveCloudPlayer.setDisplay(mTextureView);
        try {
            //TODO
            //QHVCSdkConfig qhvcSdkConfig = QHVCSdk.getInstance().getConfig();
            Map<String, Object> options = new HashMap<>();
            options.put(IQHVCPlayerAdvanced.KEY_OPTION_POSITION, initPlayPos);
            mLiveCloudPlayer.setDataSource(IQHVCPlayer.PLAYTYPE_VOD, localServerUrl, "qvod_demo_q1", "", options);
        } catch (Exception e) {
            Logger.e(TAG, e.getMessage());
            Toast.makeText(this, "数据源异常", Toast.LENGTH_SHORT).show();
            return;
        }

        mLiveCloudPlayer.setOnPreparedListener(new IQHVCPlayer.OnPreparedListener() {
            @Override
            public void onPrepared() {
                Logger.d(TAG, "onPrepared");
                mLiveCloudPlayer.start();
            }
        });

        mLiveCloudPlayer.setOnInfoListener(new IQHVCPlayer.OnInfoListener() {
            @Override
            public void onInfo(int handle, int what, int extra) {
                Logger.d(TAG, "onInfo handle: " + handle + " what : " + what + " extra : " + extra);

                if (what == IQHVCPlayer.INFO_LIVE_PLAY_START) {
                    if (mImageViews[mViewPager.getCurrentItem() % mImageViews.length] != null) {
                        mImageViews[mViewPager.getCurrentItem() % mImageViews.length].setVisibility(View.GONE);
                    }

                    String title = mPlayList.get(mViewPager.getCurrentItem() % mImageViews.length).getTitle();
                    if (!TextUtils.isEmpty(title)) {
                        mHeaderView.setLeftText(title);
                    } else {
                        String rid = mPlayList.get(mViewPager.getCurrentItem() % mImageViews.length).getRid();
                        mHeaderView.setLeftText(rid);
                    }

                    mPlayView.setVisibility(View.GONE);
                    mPauseView.setVisibility(View.VISIBLE);
                } else if (what == IQHVCPlayer.INFO_DEVICE_RENDER_ERR) {

                    // err
                    if (Logger.LOG_ENABLE) {
                        Logger.e(TAG, "dvrender err");
                    }
                } else if (what == IQHVCPlayer.INFO_DEVICE_RENDER_QUERY_SURFACE) {

                    if (mTextureView != null) {
                        if (mLiveCloudPlayer != null && !mLiveCloudPlayer.isPaused()) {
                            mTextureView.render_proc(PlayerCallback.DEVICE_RENDER_QUERY_SURFACE, 0/*不使用此变量*/);
                        }
                    }
                } else if (what == IQHVCPlayer.INFO_RENDER_RESET_SURFACE) {

                    if (mTextureView != null) {
                        mTextureView.pauseSurface();
                    }
                } else if (what == IQHVCPlayer.INFO_PLAY_H265) {
                    String title = mPlayList.get(mViewPager.getCurrentItem() % mImageViews.length).getTitle();
                    if (!TextUtils.isEmpty(title)) {
                        mHeaderView.setLeftText(title + "(H265)");
                    } else {
                        String rid = mPlayList.get(mViewPager.getCurrentItem() % mImageViews.length).getRid();
                        mHeaderView.setLeftText(rid + "(H265)");
                        Logger.d(TAG, "播放H265");
                    }
                }
            }
        });

        mLiveCloudPlayer.setOnErrorListener(new IQHVCPlayer.OnErrorListener() {
            @Override
            public boolean onError(int handle, int what, int extra) {
                Toast.makeText(LocalServerPlayerActivity.this, "播放失败：what=" + what + ", extra=" + extra, Toast.LENGTH_SHORT).show();
                return false;
            }
        });

        mLiveCloudPlayer.setOnProgressChangeListener(new IQHVCPlayer.onProgressChangeListener() {
            @Override
            public void onProgressChange(int handle, int total, int progress) {
                mSeekBar.setProgress((progress * 100) / total);
                mCurrTimeTextView.setText(AndroidUtil.getTimeString(progress));
                mTotalTimeTextView.setText(AndroidUtil.getTimeString(total));
            }
        });

        mLiveCloudPlayer.setOnBufferingUpdateListener(new IQHVCPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(int handle, int percent) {
                if (mLiveCloudPlayer != null) {
                    mSeekBar.setSecondaryProgress(percent);
                }
            }
        });

        mLiveCloudPlayer.setOnCompletionListener(new IQHVCPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(int handle) {
                if (mLiveCloudPlayer != null) {
                    mLiveCloudPlayer.seekTo(0);
                }
            }
        });
        mLiveCloudPlayer.setOnVideoSizeChangedListener(new IQHVCPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(int handle, int width, int height) {
                if(mTextureView != null){
                    mTextureView.setVideoRatio((float) width / (float) height);
                }
            }
        });

        try {
            mLiveCloudPlayer.prepareAsync();
        } catch (IllegalStateException e) {
            Logger.e(TAG, e.getMessage());
            Toast.makeText(this, "prepareAsync 异常", Toast.LENGTH_SHORT).show();
            return;
        }

        preCache();
    }

    private void stopPlay() {
        Logger.d(TAG, "stopPlay");
        if (mLiveCloudPlayer != null) {
            for (int i = 0; i < mPlayList.size(); i++) {
                if (mImageViews[i] != null) {
                    mImageViews[i].setVisibility(View.VISIBLE);
                }
            }

            if (mTextureView != null) {
                mTextureView.stopRender();
            }
            mLiveCloudPlayer.stop();
            mLiveCloudPlayer.release();
            mLiveCloudPlayer = null;
        }

        LocalServer.enableCache(LocalServerSettingConfig.ENABLE_CACHE_WHEN_PAUSE);
    }

    private void preCache() {
        int nextId = mCurIndex + 1;
        nextId = nextId >= mPlayList.size() ? 0 : nextId;
        VideoItemData o = mPlayList.get(nextId);
        LocalServer.doPrecache(o.getRid(), o.getUrl(), 800);
    }

    private void showCacheInfo(String rid, String url) {
        boolean cacheFinished = LocalServer.isCacheFinished(rid, url);
        if (cacheFinished) {
            Toast.makeText(this, "缓存完成", Toast.LENGTH_SHORT).show();
        } else {
            LocalServer.CachedSize cachedSize = new LocalServer.CachedSize();
            if (LocalServer.getFileCachedSize(rid, url, cachedSize)) {
                int progress = cachedSize.totalSize != 0 ? (int) (cachedSize.cachedSize * 100.0f / cachedSize.totalSize) : -1;
                String message = String.format(Locale.getDefault(), "当前缓存进度：%d%%", progress);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "查询当前缓存进度失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener() {

        private int direction = 0;// 方向
        private int oldOffset;
        private boolean isScrolling = false;

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (isScrolling) {
                if (oldOffset > positionOffsetPixels) {
                    // 右
                    direction = 1;
                } else if (oldOffset < positionOffsetPixels) {
                    // 左
                    direction = 2;
                } else {
                    direction = 0;
                }
                oldOffset = positionOffsetPixels;
            }
        }

        @Override
        public void onPageSelected(int position) {
            if (direction == 2) {
                if (++mCurIndex > mImageViews.length - 1) {
                    mCurIndex = 0;
                }
            } else if (direction == 1) {
                if (--mCurIndex < 0) {
                    mCurIndex = mImageViews.length - 1;
                }
            } else {
                return;
            }

            startPlay(mCurIndex);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            switch (state) {
                case ViewPager.SCROLL_STATE_DRAGGING:
                    isScrolling = true;
                    break;
                case ViewPager.SCROLL_STATE_IDLE:
                    isScrolling = false;
                    break;
                case ViewPager.SCROLL_STATE_SETTLING:
                    isScrolling = false;
                    break;
            }
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        private int mProgress = 0;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Logger.v(TAG, "onProgressChanged mSeekBar=" + seekBar + " progress=" + progress + " fromUser=" + fromUser);
            mProgress = progress;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Logger.v(TAG, "onStartTrackingTouch mSeekBar=" + seekBar);
            mProgress = 0;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            Logger.v(TAG, "onStopTrackingTouch mSeekBar=" + seekBar + " mProgress=" + mProgress);
            if (mLiveCloudPlayer != null) {
                mLiveCloudPlayer.seekTo(mLiveCloudPlayer.getDuration() * mProgress / 100);
            }
        }
    };

    private class MyAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            if (mImageViews == null) {
                return 0;
        }
            return mImageViews.length;
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }

        @Override
        public void destroyItem(View container, int position, Object object) {
            Logger.v(TAG, "destroyItem position : " + position % mImageViews.length);
            ((ViewPager) container).removeView(mImageViews[position % mImageViews.length]);
        }

        @Override
        public Object instantiateItem(View container, int position) {
            Logger.v(TAG, "instantiateItem position : " + position % mImageViews.length);
            ((ViewPager) container).addView(mImageViews[position % mImageViews.length], 0);
            return mImageViews[position % mImageViews.length];
        }
    }
}
