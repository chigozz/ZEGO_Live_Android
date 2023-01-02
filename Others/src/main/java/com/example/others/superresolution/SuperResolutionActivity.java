package com.example.others.superresolution;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
import im.zego.zegoexpress.constants.ZegoPublisherState;
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoStreamResourceMode;
import im.zego.zegoexpress.constants.ZegoSuperResolutionState;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoPlayerConfig;
import im.zego.zegoexpress.entity.ZegoUser;

public class SuperResolutionActivity extends AppCompatActivity {
    String userID;
    String streamID;
    String roomID;
    ZegoExpressEngine engine;
    Long appID;
    String appSign;
    ZegoUser user;
    //Store whether the user is playing the stream
    Boolean isPlay = false;

    TextureView playView;
    TextView roomState;
    Button startPlayingButton;
    EditText playStreamIDEdit;
    EditText superResolutionStreamIDEdit;
    Switch enableSuperResolutionSwitch;
    TextView superResolutionStateText;
    TextView playerVideoSizeText;
    TextView roomInfoText;

    public static void actionStart(Activity activity) {
        Intent intent = new Intent(activity, SuperResolutionActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_resolution);
//        initData();
        bindView();
        getAppIDAndUserIDAndAppSign();
        setDefaultValue();
        initEngineAndUser();
        loginRoom();
        requestPermission();
        setStartPlayButtonEvent();
        SetSuperResolutionSwitchEvent();
        setApiCalledResult();
        setLogComponent();
        setEventHandler();
    }

    @Override
    protected void onDestroy() {
        //logout and destroy the engine.
        engine.logoutRoom(roomID);
        ZegoExpressEngine.destroyEngine(null);
        super.onDestroy();
    }

    public void bindView(){
        playView = findViewById(R.id.PlayView);
        startPlayingButton = findViewById(R.id.startPlayButton);
        roomState = findViewById(R.id.roomState);
        playStreamIDEdit = findViewById(R.id.editPlayStreamID);
        superResolutionStreamIDEdit = findViewById(R.id.editSuperResolutionStreamID);
        enableSuperResolutionSwitch = findViewById(R.id.switchEnableSuperResolution);
        superResolutionStateText = findViewById(R.id.textViewSuperResolutionState);
        playerVideoSizeText = findViewById(R.id.textViewVideoSize);
        roomInfoText = findViewById(R.id.textViewRoomInfo);
    }
    public void setDefaultValue(){
        roomID = "0036";
        streamID = "0036";
        setTitle(getString(R.string.super_resolution));
        playerVideoSizeText.setText("Player video size: ");
        superResolutionStateText.setText("Super resolution state: ");
        playStreamIDEdit.setText(streamID);
        superResolutionStreamIDEdit.setText(streamID);
        roomInfoText.setText(String.format("UserID:%s RoomID:%s", userID.toString(), roomID.toString()));
        superResolutionStateText.setText("Super resolution state: Off errorCode:0");
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
        profile.scenario = ZegoScenario.GENERAL;
        profile.application = getApplication();
        engine = ZegoExpressEngine.createEngine(profile, null);

        AppLogger.getInstance().callApi("Create ZegoExpressEngine");
        //create the user
        user = new ZegoUser(userID);
    }

    public void loginRoom(){
        //login room
        engine.loginRoom(roomID, user);
        AppLogger.getInstance().callApi("LoginRoom: %s",roomID);
        //enable the camera
        engine.enableCamera(true);
        //enable the microphone
        engine.muteMicrophone(false);
        //enable the speaker
        engine.muteSpeaker(false);
    }

    public void requestPermission() {
        String[] PERMISSIONS_STORAGE = {
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(PERMISSIONS_STORAGE, 101);
            }
        }
    }

    public void setStartPlayButtonEvent(){
        startPlayingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //if the user is playing the stream, this button is used to stop playing. Otherwise, this button is used to start publishing.
                if (isPlay){
                    engine.stopPlayingStream(streamID);
                    AppLogger.getInstance().callApi("Stop Playing Stream:%s",streamID);
                    startPlayingButton.setText(getString(R.string.start_playing));
                    isPlay = false;
                } else {
                    streamID = playStreamIDEdit.getText().toString();
                    ZegoPlayerConfig playerConfig = new ZegoPlayerConfig();
                    playerConfig.resourceMode = ZegoStreamResourceMode.ONLY_RTC;
                    engine.startPlayingStream(streamID, new ZegoCanvas(playView), playerConfig);
                    startPlayingButton.setText(getString(R.string.stop_playing));
                    AppLogger.getInstance().callApi("Start Playing Stream:%s",streamID);
                    isPlay = true;
                }
            }
        });
    }

    public void SetSuperResolutionSwitchEvent(){
        enableSuperResolutionSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                String streamID = superResolutionStreamIDEdit.getText().toString();
                engine.enableVideoSuperResolution(streamID, b);
                AppLogger.getInstance().callApi("enableVideoSuperResolution, streamID:%s, enable:%b", streamID, b);
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
            }

            @Override
            public void onPlayerVideoSizeChanged(String streamID, int width, int height) {
                super.onPlayerVideoSizeChanged(streamID, width, height);

                playerVideoSizeText.setText(String.format("Player video size: %dx%d", width, height));
            }

            @Override
            public void onPlayerVideoSuperResolutionUpdate(String streamID, ZegoSuperResolutionState state, int errorCode) {
                super.onPlayerVideoSuperResolutionUpdate(streamID, state, errorCode);

                String superResolutionStateStr = "";
                if(state == ZegoSuperResolutionState.ON)
                {
                    superResolutionStateStr = "Super resolution state: On";
                }
                else if(state == ZegoSuperResolutionState.OFF)
                {
                    superResolutionStateStr = "Super resolution state: Off";
                }

                superResolutionStateText.setText(String.format("%s errorCode:%d", superResolutionStateStr, errorCode));

                AppLogger.getInstance().i(String.format("onPlayerVideoSuperResolutionUpdate, streamID:%s, state:%d, errorCode:%d", streamID, state.value(), errorCode));
            }
        });
    }
}
