
package com.qihoo.videocloud.interactbrocast;

import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.qihoo.livecloud.interact.api.QHVCInteractiveConstant;
import com.qihoo.livecloud.interact.api.QHVCInteractiveEventHandler;
import com.qihoo.livecloud.interact.api.QHVCInteractiveKit;
import com.qihoo.livecloud.interact.api.QHVCInteractiveUtils;
import com.qihoo.livecloud.tools.Constants;
import com.qihoo.livecloud.tools.Logger;
import com.qihoo.livecloudrefactor.R;
import com.qihoo.videocloud.interactbrocast.adapter.PartyGridViewAdapter;
import com.qihoo.videocloud.interactbrocast.data.InteractGlobalManager;
import com.qihoo.videocloud.interactbrocast.main.InteractCallBackEvent;
import com.qihoo.videocloud.interactbrocast.main.InteractCallback;
import com.qihoo.videocloud.interactbrocast.main.InteractConstant;
import com.qihoo.videocloud.interactbrocast.main.WorkerThread;
import com.qihoo.videocloud.interactbrocast.modle.InteractRoomModel;
import com.qihoo.videocloud.interactbrocast.modle.InteractUserModel;
import com.qihoo.videocloud.interactbrocast.net.InteractServerApi;
import com.qihoo.videocloud.interactbrocast.party.PartyRoleItem;
import com.qihoo.videocloud.utils.LibTaskController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by liuyanqing on 2018/3/7.
 */

public class PartyMainActivity extends BaseActivity implements InteractCallBackEvent, View.OnClickListener {

    public static final int MODE_AUDIENCE = 1; //观众模式
    public static final int MODE_PLAYER = 2; //参与轰趴模式

    private GridView mPartyGridView;
    private PartyGridViewAdapter mGridAdapter;

    private ArrayList<PartyRoleItem> mAllShowData = new ArrayList<>();
    private Hashtable<String, PartyRoleItem> mCurrUserHash = new Hashtable<>(); //存放当前房间内参与轰趴的主播
    private ScheduledExecutorService mExecutorService = null;

    private int currPlayMode; //当前模式(参与模式 或 观众模式)

    private PartyRoleItem mSelfRoleItem; //自己的Item
    private View selfView; //自己的预览View
    private Bitmap mDefaultImage;

    private InteractUserModel mSelfUserModel;
    private String myUid;

    private InteractRoomModel iteractPartyRoom;
    private String roomName;
    private String roomId;
    private int onlineNum;

    //Test Todo
    TextView joinBtn;
    TextView leaveBtn;

    //连麦
    private WorkerThread mWorker;
    private QHVCInteractiveKit mInteractEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_party_main_layout);

        initData();
        initView();

        initWorker();

        loadInteractEngine();
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onDestroy() {
        leaveChannel();
        super.onDestroy();
    }

    private void initView() {
        mPartyGridView = (GridView) findViewById(R.id.party_gridView);
        mGridAdapter = new PartyGridViewAdapter(PartyMainActivity.this, R.layout.party_gridview_item_layout, mAllShowData);
        int itemw = mScreenWidth / 2;
        int itemh = (mScreenHeight - getStatusBarHeight() - getNavigationHeight()) / 3;
        mGridAdapter.setItemWidth(itemw);
        mGridAdapter.setItemHeight(itemh);
        Logger.d(InteractConstant.TAG, InteractConstant.TAG + ", mGridAdapter，width:" + itemw + ", height:" + itemh + ", screenw: " + mScreenWidth + ", screenH: " + mScreenHeight +
                ",statusBarHeight" + getStatusBarHeight() + ", NavigationHeight: " + getNavigationHeight());
        mPartyGridView.setAdapter(mGridAdapter);

        //TODO for test
        joinBtn = (TextView) findViewById(R.id.button_join);
        joinBtn.setOnClickListener(this);
        leaveBtn = (TextView) findViewById(R.id.button_leave);
        leaveBtn.setOnClickListener(this);
        switch (currPlayMode) {
            case MODE_AUDIENCE:
                leaveBtn.setVisibility(View.GONE);
                break;
            case MODE_PLAYER:
                joinBtn.setVisibility(View.GONE);
                break;
        }
    }

    private void initData() {
        iteractPartyRoom = (InteractRoomModel) getIntent()
                .getSerializableExtra(InteractConstant.INTENT_EXTRA_INTERACT_ROOM_DATA);
        if (iteractPartyRoom != null) {
            roomName = iteractPartyRoom.getRoomName();
            roomId = iteractPartyRoom.getRoomId();
            onlineNum = iteractPartyRoom.getOnlineNum();
        }

        int playMode = getIntent().getIntExtra(InteractConstant.INTENT_EXTRA_USER_IDENTITY, InteractConstant.USER_IDENTITY_AUDIENCE);
        switch (playMode) {
            case InteractConstant.USER_IDENTITY_AUDIENCE:
                this.currPlayMode = MODE_AUDIENCE;
                break;
            case InteractConstant.USER_IDENTITY_ANCHOR:
                this.currPlayMode = MODE_PLAYER;
                break;
        }

        TypedArray imgs = getResources().obtainTypedArray(R.array.image_ids);
        int id = imgs.getResourceId(0, -1);
        mDefaultImage = BitmapFactory.decodeResource(getResources(), id);

        mSelfUserModel = InteractGlobalManager.getInstance().getUser();
        if (mSelfUserModel != null) {
            myUid = mSelfUserModel.getUserId();
        }
    }

    private void startParty() {
        initWorker();
        mInteractEngine = mWorker.getInteractEngine();

        if (currPlayMode == MODE_PLAYER) {
            createMyself();
            doConfigEngine(QHVCInteractiveConstant.CLIENT_ROLE_BROADCASTER);
        } else {
            doConfigEngine(QHVCInteractiveConstant.CLIENT_ROLE_AUDIENCE);
        }
        InteractCallback.getInstance().addCallBack(PartyMainActivity.this);
        //设置小流参数
        if (mInteractEngine != null) {
            mInteractEngine.enableDualStreamMode(true); //开启双流模式（即大小流）
            mInteractEngine.setLowStreamVideoProfile(180, 320, 15, 180);
        }

        joinChannel();
    }

    private void createMyself() {
        if (mSelfRoleItem == null) {
            mSelfRoleItem = new PartyRoleItem(myUid, mDefaultImage);
            if (selfView == null) {
                selfView = QHVCInteractiveUtils.CreateRendererView(PartyMainActivity.this);
            }
            //if (selfView instanceof SurfaceView) {
            //    SurfaceView surfaceV = (SurfaceView) selfView;
            //    surfaceV.setZOrderOnTop(true);
            //    surfaceV.setZOrderMediaOverlay(true);
            //}

            mSelfRoleItem.setVideoView(selfView);

            mCurrUserHash.put(myUid, mSelfRoleItem);
        }
        startPreview();

        //        mAllShowData.add(selfItem);
        //
        //        for (int i = 0; i < 5; i++) {
        //            PartyRoleItem item = new PartyRoleItem("0", mDefaultImage);
        //            mAllShowData.add(item);
        //        }
        //
        //        mGridAdapter.notifyDataSetChanged();
    }

    private void stopPreview() {
        if (mSelfRoleItem != null) {
            mCurrUserHash.remove(myUid);
            if (mWorker != null) {
                mWorker.preview(false, mSelfRoleItem.getVideoView(), myUid);
            }
            mSelfRoleItem = null;
        }
    }

    private void startPreview() {
        if (mWorker != null) {
            mWorker.preview(true, mSelfRoleItem.getVideoView(), myUid);
        }
    }

    private void initWorker() {
        if (mWorker == null) {
            //TODO 暂时不考虑美颜
            mWorker = new WorkerThread(false);
        }

        mWorker.start();
        mWorker.waitForReady();
    }

    private void loadInteractEngine() {
        InteractCallback.getInstance().addCallBack(PartyMainActivity.this);

        Map<String, String> optionInfo = new HashMap<>(); //TODO
        mWorker.loadEngine(roomId, myUid, optionInfo);
    }

    private void doConfigEngine(int cRole) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        int prefIndex = pref.getInt(InteractConstant.PrefManager.PREF_PROPERTY_PROFILE_IDX,
                InteractConstant.DEFAULT_PROFILE_IDX);
        if (prefIndex > InteractConstant.VIDEO_PROFILES.length - 1) {
            prefIndex = InteractConstant.DEFAULT_PROFILE_IDX;
        }
        int vProfile = InteractConstant.VIDEO_PROFILES[prefIndex];

        //int vProfile = 30;
        mWorker.configEngine(cRole, vProfile, Constants.EMode.EMODE_PORTRAIT);
    }

    private void joinChannel() {
        if (mWorker != null) {
            mWorker.joinChannel();
        } else {
            Logger.w(InteractConstant.TAG, "Error! joinChannel failed, mWorker is null!");
            showToast("Error! mWorker is null!");
        }
    }

    /**
     * 自动查询房间用户列表， 暂定间隔10S
     */
    private void autoQueryUserList() {
        if (mExecutorService == null) {
            mExecutorService = Executors.newScheduledThreadPool(1);

            ScheduledFuture scheduledFuture = mExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    getUserListFromServer();
                }
            }, 0, 3, TimeUnit.SECONDS);
        }
    }

    private void getUserListFromServer() {
        int[] userIdentitys = new int[] {
                InteractConstant.USER_IDENTITY_ANCHOR,
                InteractConstant.USER_IDENTITY_GUEST
        };

        InteractServerApi.getRoomUserList(myUid, roomId, userIdentitys,
                new InteractServerApi.ResultCallback<List<InteractUserModel>>() {

                    @Override
                    public void onSuccess(List<InteractUserModel> data) {

                        mAllShowData.clear();
                        if (data != null && data.size() > 0) {
                            PartyRoleItem roleItem;
                            for (InteractUserModel userModel : data) {
                                if (mCurrUserHash.containsKey(userModel.getUserId())) {
                                    roleItem = mCurrUserHash.get(userModel.getUserId());
                                } else {
                                    roleItem = new PartyRoleItem(userModel.getUserId(), mDefaultImage);
                                    View videoView = QHVCInteractiveUtils.CreateRendererView(PartyMainActivity.this);
                                    roleItem.setVideoView(videoView);

                                    //TODO
                                    mInteractEngine.setupRemoteVideo(videoView, QHVCInteractiveConstant.RenderMode.RENDER_MODE_HIDDEN,
                                            userModel.getUserId(), "");
                                }
                                mAllShowData.add(roleItem);
                            }

                            if (mGridAdapter != null) {
                                mGridAdapter.notifyDataSetChanged();
                            }
                        }

                        mCurrUserHash.clear();
                        for (PartyRoleItem roleItem : mAllShowData) {
                            mCurrUserHash.put(roleItem.getUserId(), roleItem);
                        }
                    }

                    @Override
                    public void onFailed(int errCode, String errMsg) {
                        Logger.d(InteractConstant.TAG, InteractConstant.TAG + ", getRoomUserList failed.. errCode: " + errCode + ", errMsg: " + errMsg);
                    }
                });
    }

    private void leaveChannel() {
        if (mWorker != null) {
            mWorker.leaveChannel(roomId);
        }
        LibTaskController.postDelayed(new Runnable() {
            @Override
            public void run() {
                exitWorker();
            }
        }, 3000);

    }

    private void exitWorker() {
        if (mExecutorService != null) {
            mExecutorService.shutdown();
            try {
                mExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mExecutorService = null;
        }

        if (mWorker != null) {
            mWorker.exit();
        }
        mWorker = null;
        InteractCallback.getInstance().removeCallBack(this);

        finish();
    }

    private void joinRoomToServer() {
        InteractServerApi.joinRoom(myUid, roomId, InteractConstant.USER_IDENTITY_AUDIENCE,
                new InteractServerApi.ResultCallback<InteractRoomModel>() {
                    @Override
                    public void onSuccess(InteractRoomModel data) {
                        Toast.makeText(PartyMainActivity.this, "加入频道成功！", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailed(int errCode, String errMsg) {
                        showToast("joinRoomToServer fail!, errcode:" + errCode + ", " + errMsg);
                    }
                });
    }

    private void changeUserdentity(int userIdentity) {
        InteractServerApi.changeUserdentity(myUid, roomId, userIdentity, new InteractServerApi.ResultCallback<InteractRoomModel>() {

            @Override
            public void onSuccess(InteractRoomModel data) {
                getUserListFromServer();
            }

            @Override
            public void onFailed(int errCode, String errMsg) {
                Logger.e(InteractConstant.TAG, InteractConstant.TAG + ", changeUserdentity failed, errCode : " + errCode + ", errMsg:" + errMsg);
                showToast("changeUserdentity fail!, errcode:" + errCode + ", " + errMsg);
            }
        });
    }

    @Override
    public void onLoadEngineSuccess(String roomId, String uid) {
        //do nothing
    }

    @Override
    public void onJoinChannelSuccess(String channel, String uid, int elapsed) {
        if (currPlayMode == MODE_AUDIENCE) {
            joinRoomToServer();
        }
        autoQueryUserList();
        Logger.d(InteractConstant.TAG, InteractConstant.TAG + ", onJoinChannelSuccess(), channel: " + channel + ", uid: " + uid);
    }

    @Override
    public void onUserOffline(String uid, int reason) {

    }

    @Override
    public void onLeaveChannel(QHVCInteractiveEventHandler.RtcStats stats) {
        exitWorker();
    }

    @Override
    public void onError(int errType, int errCode) {
        switch (errType) {
            case QHVCInteractiveConstant.ErrorType.JOIN_ERR:
                //加入频道失败，需退出后重新加入频道 TODO
                showToast("加入频道失败！" + errCode);
                leaveChannel();
                break;
            case QHVCInteractiveConstant.ErrorType.LOADENGINE_ERROR:
                Logger.w(InteractConstant.TAG, "LoadInteractEngine failed in PartyMainActivity.. errCode: " + errCode);
                showToast("加载互动直播引擎失败， errCode: " + errCode);
                leaveChannel();
                break;
            case QHVCInteractiveConstant.ErrorType.PUBLISH_ERR:
                //先不用处理
                break;
        }
    }

    @Override
    public void onRtcStats(QHVCInteractiveEventHandler.RtcStats stats) {

    }

    @Override
    public void onConnectionLost(int errCode) {

    }

    @Override
    public void onFirstRemoteVideoFrame(String uid, int width, int height, int elapsed) {

    }

    @Override
    public void onRemoteVideoStats(QHVCInteractiveEventHandler.RemoteVideoStats stats) {

    }

    @Override
    public void onLocalVideoStats(QHVCInteractiveEventHandler.LocalVideoStats stats) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_join:
                //加入轰趴
                createMyself();
                changeUserdentity(InteractConstant.USER_IDENTITY_ANCHOR);
                doConfigEngine(QHVCInteractiveConstant.CLIENT_ROLE_BROADCASTER);
                joinBtn.setVisibility(View.GONE);
                leaveBtn.setVisibility(View.VISIBLE);
                break;
            case R.id.button_leave:
                stopPreview();
                changeUserdentity(InteractConstant.USER_IDENTITY_AUDIENCE);
                doConfigEngine(QHVCInteractiveConstant.CLIENT_ROLE_AUDIENCE);
                joinBtn.setVisibility(View.VISIBLE);
                leaveBtn.setVisibility(View.GONE);
                break;
        }

    }
}
