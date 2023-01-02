package com.example.others.screensharing;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.others.R;

import org.json.JSONObject;

import im.zego.commontools.logtools.AppLogger;
import im.zego.commontools.logtools.LogView;
import im.zego.commontools.logtools.logLinearLayout;
import im.zego.commontools.uitools.ZegoViewUtil;
import im.zego.keycenter.KeyCenter;
import im.zego.keycenter.UserIDHelper;
import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoApiCalledEventHandler;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoPlayerState;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoVideoSourceType;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoUser;
import im.zego.zegoexpress.entity.ZegoVideoConfig;

public class ScreenSharingActivity extends AppCompatActivity {

    TextView userIDText;
    EditText roomIDEdit;
    EditText publishStreamIDEdit;
    Button startScreenCaptureButton;
    EditText playStreamIDEdit;
    Button playButton;
    TextureView playView;
    TextView roomState;
    EditText encodeResolutionWidth;
    EditText encodeResolutionHeight;
    EditText frameRateEdit;
    EditText bitrateEdit;

    String userID;
    String publishStreamID;
    String playStreamID;
    String roomID;
    ZegoExpressEngine engine;
    Long appID;
    String appSign;
    ZegoUser user;
    static MediaProjectionManager mMediaProjectionManager;
    private static final int REQUEST_CODE = 1001;
    private Intent service;
    public static MediaProjection mMediaProjection;
    private static final int DEFAULT_VIDEO_WIDTH = 360;
    private static final int DEFAULT_VIDEO_HEIGHT = 640;

    //Store whether the user is playing the stream
    Boolean isPlay = false;
    //Store whether the user is publishing the stream
    Boolean isPublish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_sharing);
        bindView();
        requestPermission();
        setLogComponent();
        getAppIDAndUserIDAndAppSign();
        setDefaultValue();
        initEngineAndUser();
        prepareScreenCapture();
        setPlayButtonEvent();
        setEventHandler();
        setStartScreenCaptureButtonEvent();
        setApiCalledResult();
    }
    public void bindView(){
        userIDText = findViewById(R.id.userID);
        roomIDEdit = findViewById(R.id.roomIDEdit);
        publishStreamIDEdit = findViewById(R.id.publishIDEdit);
        startScreenCaptureButton = findViewById(R.id.screenCaptureButton);
        playStreamIDEdit = findViewById(R.id.editPlayStreamID);
        playButton = findViewById(R.id.playButton);
        playView = findViewById(R.id.playView);
        roomState = findViewById(R.id.roomState);
        encodeResolutionWidth = findViewById(R.id.encodeResolutionWidth);
        encodeResolutionHeight = findViewById(R.id.encodeResolutionHeight);
        frameRateEdit = findViewById(R.id.frameRateEdit);
        bitrateEdit = findViewById(R.id.bitrateEdit);
    }
    public void setDefaultValue(){
        roomID = "0033";
        publishStreamID = "0033";
        playStreamID = "0033";
        userIDText.setText(userID);
        setTitle(getString(R.string.screen_sharing));
    }
    //get appID and userID and appSign
    public void getAppIDAndUserIDAndAppSign(){
        appID = KeyCenter.getInstance().getAppID();
        userID = UserIDHelper.getInstance().getUserID();
        appSign = KeyCenter.getInstance().getAppSign();
    }
    public void initEngineAndUser(){
        // Initialize ZegoExpressEngine
        ZegoEngineProfile profile = new ZegoEngineProfile();
        profile.appID = appID;
        profile.appSign = appSign;
        profile.scenario = ZegoScenario.DEFAULT;
        profile.application = getApplication();
        engine = ZegoExpressEngine.createEngine(profile, null);

        AppLogger.getInstance().callApi("Create ZegoExpressEngine");
        //create the user
        user = new ZegoUser(userID);
    }

    public void setVideoConfig() {
        ZegoVideoConfig videoConfig = engine.getVideoConfig(ZegoPublishChannel.AUX);
        videoConfig.encodeHeight = Integer.parseInt(encodeResolutionHeight.getText().toString());
        videoConfig.encodeWidth = Integer.parseInt(encodeResolutionWidth.getText().toString());;
        videoConfig.fps = Integer.parseInt(frameRateEdit.getText().toString());
        videoConfig.bitrate = Integer.parseInt(bitrateEdit.getText().toString());
        engine.setVideoConfig(videoConfig, ZegoPublishChannel.AUX);
    }

    public void  prepareScreenCapture(){
        engine.enableHardwareEncoder(true);
        engine.setVideoSource(ZegoVideoSourceType.ZEGO_VIDEO_SOURCE_SCREEN_CAPTURE, ZegoPublishChannel.AUX);
    }

    public void setStartScreenCaptureButtonEvent(){
        startScreenCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPublish){
                    AppLogger.getInstance().callApi("Stop Publishing Stream: %s",publishStreamID);
                    startScreenCaptureButton.setText(getString(R.string.start_screen_capture));
                    engine.stopScreenCapture();
                    engine.stopPublishingStream(ZegoPublishChannel.AUX);
                    engine.logoutRoom(roomID);
                    isPublish = false;
                } else {
                    setVideoConfig();
                    engine.startScreenCapture();
                    loginRoom();
                    publishStreamID = publishStreamIDEdit.getText().toString();
                    engine.startPublishingStream(publishStreamID, ZegoPublishChannel.AUX);
                    startScreenCaptureButton.setText(getString(R.string.stop_screen_capture));
                    AppLogger.getInstance().callApi("Start Publishing Stream: %s",publishStreamID);
                    isPublish = true;
                }
            }
        });
    }
    public void loginRoom(){
        roomID = roomIDEdit.getText().toString();
        //login room
        engine.loginRoom(roomID, user);
        AppLogger.getInstance().callApi("LoginRoom: %s",roomID);
    }
    public void setPlayButtonEvent(){
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //if the user is playing the stream, this button is used to stop playing. Otherwise, this button is used to start publishing.
                if (isPlay){
                    engine.stopPlayingStream(playStreamID);
                    AppLogger.getInstance().callApi("Stop Playing Stream:%s",playStreamID);
                    playButton.setText(getString(R.string.start_playing));
                    isPlay = false;
                } else {
                    playStreamID = playStreamIDEdit.getText().toString();
                    engine.startPlayingStream(playStreamID, new ZegoCanvas(playView));
                    playButton.setText(getString(R.string.stop_playing));
                    AppLogger.getInstance().callApi("Start Playing Stream:%s",playStreamID);
                    isPlay = true;
                }
            }
        });
    }
    public void setEventHandler(){
        engine.setEventHandler(new IZegoEventHandler() {
            // The callback triggered when the room connection state changes.
            @Override
            public void onRoomStateChanged(String roomID, ZegoRoomStateChangedReason reason, int errorCode, JSONObject extendedData) {
                ZegoViewUtil.UpdateRoomState(roomState, reason);
            }

            // The callback triggered when the state of stream playing changes.
            @Override
            public void onPlayerStateUpdate(String streamID, ZegoPlayerState state, int errorCode, JSONObject extendedData) {
                super.onPlayerStateUpdate(streamID, state, errorCode, extendedData);
                // If the state is PLAYER_STATE_NO_PLAY and the errcode is not 0, it means that stream playing has failed and
                // no more retry will be attempted by the engine. At this point, the failure of stream playing can be indicated
                // on the UI of the App.
                if(errorCode != 0 && state.equals(ZegoPlayerState.NO_PLAY)) {
                    if (isPlay) {
                        playButton.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.crossEmoji) + getString(R.string.stop_playing));
                    }
                } else {
                    if (isPlay) {
                        playButton.setText(ZegoViewUtil.GetEmojiStringByUnicode(ZegoViewUtil.checkEmoji) + getString(R.string.stop_playing));
                    }
                }
            }

        });
    }
    public void setApiCalledResult(){
        // Update log with api called results
        ZegoExpressEngine.setApiCalledCallback(new IZegoApiCalledEventHandler() {
            @Override
            public void onApiCalledResult(int errorCode, String funcName, String info) {
                super.onApiCalledResult(errorCode, funcName, info);
                if (errorCode == 0){
                    AppLogger.getInstance().success("[%s]:%s", funcName, info);
                } else {
                    AppLogger.getInstance().fail("[%d]%s:%s", errorCode, funcName, info);
                }
            }
        });
    }

    // Set log component. It includes a pop-up dialog.
    public void setLogComponent(){
        logLinearLayout logHiddenView = findViewById(R.id.logView);
        logHiddenView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogView logview = new LogView(getApplicationContext());
                logview.show(getSupportFragmentManager(),null);
            }
        });
    }
    public static void actionStart(Activity activity) {
        Intent intent = new Intent(activity, ScreenSharingActivity.class);
        activity.startActivity(intent);
    }
    public void requestPermission() {
        String[] PERMISSIONS_STORAGE = {
                "android.permission.RECORD_AUDIO",
                "android.permission.FOREGROUND_SERVICE"};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE") != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(PERMISSIONS_STORAGE, 101);
            }
        }
    }
    @Override
    protected void onDestroy() {
        ZegoExpressEngine.destroyEngine(null);
        super.onDestroy();
    }
}