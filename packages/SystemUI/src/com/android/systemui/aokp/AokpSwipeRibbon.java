/*
 * Copyright (C) 2013 The Android Open Kand Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.aokp;

import com.android.systemui.R;

import java.lang.IllegalArgumentException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.AwesomeAnimationHelper;
import com.android.internal.util.aokp.BackgroundAlphaColorDrawable;
import com.android.systemui.aokp.RibbonGestureCatcherView;

public class AokpSwipeRibbon extends LinearLayout {
    public static final String TAG = "NAVIGATION BAR RIBBON";

    private Context mContext;
    private RibbonGestureCatcherView mGesturePanel;
    public FrameLayout mPopupView;
    public FrameLayout mContainerFrame;
    public WindowManager mWindowManager;
    private SettingsObserver mSettingsObserver;
    private LinearLayout mRibbon;
    private LinearLayout mRibbonMain;
    private Button mBackGround;
    private boolean mText, mColorize, hasNavBarByDefault, NavBarEnabled, navAutoHide, mNavBarShowing, mVib, mHideIme;
    private int mHideTimeOut = 5000;
    private boolean showing = false;
    private boolean animating = false;
    private int mRibbonNumber, mLocationNumber, mSize, mColor, mTextColor, mOpacity, animationIn,
        animationOut, animTogglesIn, animTogglesOut, mIconLoc, mPad, mAnimDur, mDismiss, mAnim;
    private ArrayList<String> shortTargets = new ArrayList<String>();
    private ArrayList<String> longTargets = new ArrayList<String>();
    private ArrayList<String> customIcons = new ArrayList<String>();
    private String mLocation;
    private Handler mHandler;
    private boolean[] mEnableSides = new boolean[3];
    private boolean flipped = false;
    private Vibrator vib;

    private ArrayList<LinearLayout> mRows = new ArrayList<LinearLayout>();
    private ScrollView mRibbonSV;
    private Animation mAnimationIn;
    private Animation mAnimationOut;
    private int visible = 0;
    private int mDisabledFlags = 0;

    private BluetoothController bluetoothController;
    private NetworkController networkController;
    private BatteryController batteryController;
    private LocationController locationController;
    private BrightnessController brightnessController;

    public void setControllers(BluetoothController bt, NetworkController net,
            BatteryController batt, LocationController loc, BrightnessController screen) {
        bluetoothController = bt;
        networkController = net;
        batteryController = batt;
        locationController = loc;
        brightnessController = screen;
        updateSettings();
    }

    private static final LinearLayout.LayoutParams backgroundParams = new LinearLayout.LayoutParams(
            LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

    public AokpSwipeRibbon(Context context, AttributeSet attrs, String location) {
        super(context, attrs);
        mContext = context;
        mLocation = location;
        setRibbonNumber();
        IntentFilter filter = new IntentFilter();
        filter.addAction(RibbonReceiver.ACTION_TOGGLE_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_SHOW_RIBBON);
        filter.addAction(RibbonReceiver.ACTION_HIDE_RIBBON);
        mContext.registerReceiver(new RibbonReceiver(), filter);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        vib = (Vibrator) mContext.getSystemService(mContext.VIBRATOR_SERVICE);
        mHandler = new Handler();
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
        updateSettings();
    }

    private void setRibbonNumber() {
        if (mLocation.equals("bottom")) {
            mRibbonNumber = 5;
            mLocationNumber = 2;
        } else if (mLocation.equals("left")) {
            mRibbonNumber = 2;
            mLocationNumber = 0;
        } else if (mLocation.equals("right")) {
            mRibbonNumber = 4;
            mLocationNumber = 1;
        }
    }

    public void toggleRibbonView() {
        if (showing) {
            hideRibbonView();
        } else {
            showRibbonView();
        }
    }

    public void showRibbonView() {
        if (!showing) {
            showing = true;
            WindowManager.LayoutParams params = getParams();
            params.gravity = getGravity();
            params.setTitle("Ribbon" + mLocation);
            if (mWindowManager != null) {
                mWindowManager.addView(mPopupView, params);
                mContainerFrame.startAnimation(mAnimationIn);
                if (mHideTimeOut > 0) {
                    mHandler.postDelayed(delayHide, mHideTimeOut);
                }
            }
        }
    }

    public void hideRibbonView() {
        if (mPopupView != null && showing) {
            showing = false;
            mContainerFrame.startAnimation(mAnimationOut);
        }
    }

    private Runnable delayHide = new Runnable() {
        public void run() {
            if (showing) {
                hideRibbonView();
            }
        }
    };

    private WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                mLocation.equals("bottom") ? WindowManager.LayoutParams.MATCH_PARENT
                    : WindowManager.LayoutParams.WRAP_CONTENT,
                mLocation.equals("bottom") ? WindowManager.LayoutParams.WRAP_CONTENT
                    : WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        return params;
    }

    private int getGravity() {
        int gravity = 0;
        if (mLocation.equals("bottom")) {
            gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        } else if (mLocation.equals("left")) {
            gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        } else {
            gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        }
        return gravity;
    }

    private void setAnimation() {
        if (mLocation.equals("bottom")) {
            animationIn = com.android.internal.R.anim.slide_in_up_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_down_ribbon;
        } else if (mLocation.equals("left")) {
            animationIn = com.android.internal.R.anim.slide_in_left_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_left_ribbon;
            animTogglesIn = com.android.internal.R.anim.slide_in_left_ribbon;
            animTogglesOut = com.android.internal.R.anim.slide_out_right_ribbon;
        } else {
            animationIn = com.android.internal.R.anim.slide_in_right_ribbon;
            animationOut = com.android.internal.R.anim.slide_out_right_ribbon;
            animTogglesIn = com.android.internal.R.anim.slide_in_right_ribbon;
            animTogglesOut = com.android.internal.R.anim.slide_out_left_ribbon;
        }
        if (mAnim > 0) {
            int[] animArray = AwesomeAnimationHelper.getAnimations(mAnim);
            animationIn = animArray[1];
            animationOut = animArray[0];
        }
    }

    public void createRibbonView() {
        if (mGesturePanel != null) {
            try {
                mWindowManager.removeView(mGesturePanel);
            } catch (IllegalArgumentException e) {
                //If we try to remove the gesture panel and it's not currently attached.
            }
        }
        if (sideEnabled()) {
            mGesturePanel = new RibbonGestureCatcherView(mContext,null,mLocation);
            mWindowManager.addView(mGesturePanel, mGesturePanel.getGesturePanelLayoutParams());
        }
        mPopupView = new FrameLayout(mContext);
        mPopupView.removeAllViews();
        mContainerFrame = new FrameLayout(mContext);
        mContainerFrame.removeAllViews();
        if (mNavBarShowing) {
            int adjustment = mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.status_bar_height);
            mPopupView.setPadding(0, adjustment, 0, 0);
        }
        mBackGround = new Button(mContext);
        mBackGround.setClickable(false);
        mBackGround.setBackgroundColor(mColor);
        float opacity = (255f * (mOpacity * 0.01f));
        mBackGround.getBackground().setAlpha((int)opacity);
        View ribbonView = View.inflate(mContext, R.layout.aokp_swipe_ribbon, null);
        mRibbonMain = (LinearLayout) ribbonView.findViewById(R.id.ribbon_main);
        switch (mIconLoc) {
            case 0:
                mRibbonMain.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
                break;
            case 1:
                mRibbonMain.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                break;
            case 2:
                mRibbonMain.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                break;
        }
        mRibbon = (LinearLayout) ribbonView.findViewById(R.id.ribbon);
        setupRibbon();
        ribbonView.invalidate();
        mContainerFrame.addView(mBackGround, backgroundParams);
        mContainerFrame.addView(ribbonView);
        mContainerFrame.setDrawingCacheEnabled(true);
        mAnimationIn = PlayInAnim();
        mAnimationOut = PlayOutAnim();
        mPopupView.addView(mContainerFrame, backgroundParams);
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    mHandler.removeCallbacks(delayHide);
                    if (showing) {
                        hideRibbonView();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private boolean sideEnabled() {
        if (mLocation.equals("bottom") && mEnableSides[0]) {
            return true;
        } else if (mLocation.equals("left") && mEnableSides[1]) {
            return true;
        } else if (mLocation.equals("right") && mEnableSides[2]) {
            return true;
        }
        return false;
    }

    public Animation PlayInAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationIn);
            animation.setStartOffset(0);
            animation.setDuration((int) (animation.getDuration() * (mAnimDur * 0.01f)));
            return animation;
        }
        return null;
    }

    public Animation PlayOutAnim() {
        if (mRibbon != null) {
            Animation animation = AnimationUtils.loadAnimation(mContext, animationOut);
            animation.setStartOffset(0);
            animation.setDuration((int) (animation.getDuration() * (mAnimDur * 0.01f)));
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    animating = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mWindowManager.removeView(mPopupView);
                    animating = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            return animation;
        }
        return null;
    }

    private void setupRibbon() {
        mRibbon.removeAllViews();
        if (mLocation.equals("bottom")) {
            HorizontalScrollView hsv = new HorizontalScrollView(mContext);
            hsv = AokpRibbonHelper.getRibbon(mContext,
                shortTargets, longTargets, customIcons,
                mText, mTextColor, mSize, mPad, mVib, mColorize, mDismiss);
            hsv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mHandler.removeCallbacks(delayHide);
                    if (mHideTimeOut > 0) {
                        mHandler.postDelayed(delayHide, mHideTimeOut);
                    }
                    return false;
                }
            });
            mRibbon.addView(hsv);
        } else {
            mRibbonSV = new ScrollView(mContext);
            mRibbonSV = AokpRibbonHelper.getVerticalRibbon(mContext,
                shortTargets, longTargets, customIcons, mText, mTextColor,
                mSize, mPad, mVib, mColorize, mDismiss);
            mRibbonSV.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mHandler.removeCallbacks(delayHide);
                    if (mHideTimeOut > 0) {
                        mHandler.postDelayed(delayHide, mHideTimeOut);
                    }
                    return false;
                }
            });
            mRibbon.addView(mRibbonSV);
            mRibbon.setPadding(0, 0, 0, 0);
        }
    }

    public void PlayAnim(final ScrollView in, final ScrollView out, final Drawable newIcon, final String text) {
        if (mRibbon != null) {
	    Animation outAnimation = AnimationUtils.loadAnimation(mContext, animTogglesOut);
            final Animation inAnimation = AnimationUtils.loadAnimation(mContext, animTogglesIn);
            final Animation inIcon = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.fade_in);
            final Animation outIcon = AnimationUtils.loadAnimation(mContext, com.android.internal.R.anim.fade_out);
            inIcon.setDuration((int) (250 * (mAnimDur * 0.01f)));
            inIcon.setStartOffset(0);
            outIcon.setStartOffset(0);
            outIcon.setDuration((int) (250 * (mAnimDur * 0.01f)));
            outAnimation.setStartOffset(0);
            outAnimation.setDuration((int) (250 * (mAnimDur * 0.01f)));
            outAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    animating = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mRibbon.removeView(out);
                    mRibbon.addView(in);
                    in.startAnimation(inAnimation);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            inAnimation.setStartOffset(0);
            inAnimation.setDuration((int) (250 * (mAnimDur * 0.01f)));
            inAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    animating = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            out.startAnimation(outAnimation);
        }
    }

    protected void updateSwipeArea() {
        final boolean showingIme = ((visible & InputMethodService.IME_VISIBLE) != 0);
        if (mGesturePanel != null) {
            mGesturePanel.setViewVisibility(showingIme);
        }
    }

    public void setNavigationIconHints(int hints) {
          if (hints == visible) return;

        if (mHideIme) {
             visible = hints;
             updateSwipeArea();
        }
    }

    public void setDisabledFlags(int disabledFlags) {
        if (disabledFlags == mDisabledFlags) return;

        if (mHideIme) {
            mDisabledFlags = disabledFlags;
            updateSwipeArea();
        }
    }

    private boolean deviceSupportsTelephony() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_SHORT[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_LONG[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TARGETS_ICONS[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_RIBBON_TEXT[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SIZE[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DRAG_HANDLE_LOCATION[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_TEXT_COLOR[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_SPACE[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_VIBRATE[mRibbonNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ICON_COLORIZE[mRibbonNumber]), false, this);
            for (int i = 0; i < 3; i++) {
	            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ENABLE_RIBBON_LOCATION[i]), false, this);
            }
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_SHOW_NOW), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_HIDE_TIMEOUT[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_OPACITY[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SWIPE_RIBBON_COLOR[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_DISMISS[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ANIMATION_DURATION[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_ANIMATION_TYPE[mLocationNumber]), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.RIBBON_HIDE_IME[mLocationNumber]), false, this);

            if (mLocationNumber < 2) {
                resolver.registerContentObserver(Settings.System.getUriFor(
                        Settings.System.RIBBON_ICON_LOCATION[mLocationNumber]), false, this);
            }

        }
         @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
   protected void updateSettings() {
        ContentResolver cr = mContext.getContentResolver();
        shortTargets = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_SHORT[mRibbonNumber]);
        longTargets = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_LONG[mRibbonNumber]);
        customIcons = Settings.System.getArrayList(cr,
                 Settings.System.RIBBON_TARGETS_ICONS[mRibbonNumber]);
        mText = Settings.System.getBoolean(cr,
                 Settings.System.ENABLE_RIBBON_TEXT[mRibbonNumber], true);
        mTextColor = Settings.System.getInt(cr,
                 Settings.System.RIBBON_TEXT_COLOR[mRibbonNumber], -1);
        mSize = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SIZE[mRibbonNumber], 0);
        mPad = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ICON_SPACE[mRibbonNumber], 5);
        mVib = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_ICON_VIBRATE[mRibbonNumber], true);
        mColorize = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_ICON_COLORIZE[mRibbonNumber], false);
        mAnimDur = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ANIMATION_DURATION[mLocationNumber], 50);
        mDismiss = Settings.System.getInt(cr,
                 Settings.System.RIBBON_DISMISS[mLocationNumber], 1);
        mHideTimeOut = Settings.System.getInt(cr,
                 Settings.System.RIBBON_HIDE_TIMEOUT[mLocationNumber], mHideTimeOut);
        mColor = Settings.System.getInt(cr,
                 Settings.System.SWIPE_RIBBON_COLOR[mLocationNumber], Color.BLACK);
        mOpacity = Settings.System.getInt(cr,
                 Settings.System.SWIPE_RIBBON_OPACITY[mLocationNumber], 100);
        mAnim = Settings.System.getInt(cr,
                 Settings.System.RIBBON_ANIMATION_TYPE[mLocationNumber], 0);
        mHideIme = Settings.System.getBoolean(cr,
                 Settings.System.RIBBON_HIDE_IME[mLocationNumber], false);
        if (mLocationNumber < 2) {
            mIconLoc = Settings.System.getInt(cr,
                     Settings.System.RIBBON_ICON_LOCATION[mLocationNumber], 0);
        }

        for (int i = 0; i < 3; i++) {
            mEnableSides[i] = Settings.System.getBoolean(cr,
                 Settings.System.ENABLE_RIBBON_LOCATION[i], false);
        }
        boolean manualNavBarHide = Settings.System.getBoolean(mContext.getContentResolver(), Settings.System.NAVIGATION_BAR_SHOW_NOW, true);
        hasNavBarByDefault = mContext.getResources().getBoolean(com.android.internal.R.bool.config_showNavigationBar);
        mNavBarShowing = (NavBarEnabled || hasNavBarByDefault) && manualNavBarHide && !navAutoHide;
        mEnableSides[0] = mEnableSides[0] && !(NavBarEnabled || hasNavBarByDefault);

        setAnimation();
        if (!showing && !animating) {
            createRibbonView();
        }
    }

    public class RibbonReceiver extends BroadcastReceiver {
        public static final String ACTION_TOGGLE_RIBBON = "com.android.systemui.ACTION_TOGGLE_RIBBON";
        public static final String ACTION_SHOW_RIBBON = "com.android.systemui.ACTION_SHOW_RIBBON";
        public static final String ACTION_HIDE_RIBBON = "com.android.systemui.ACTION_HIDE_RIBBON";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String location = intent.getStringExtra("action");
            if (ACTION_TOGGLE_RIBBON.equals(action)) {
                mHandler.removeCallbacks(delayHide);
                if (location.equals(mLocation)) {
                    toggleRibbonView();
                }
            } else if (ACTION_SHOW_RIBBON.equals(action)) {
                if (location.equals(mLocation)) {
                    if (!showing) {
                        showRibbonView();
                    }
                }
            } else if (ACTION_HIDE_RIBBON.equals(action)) {
                mHandler.removeCallbacks(delayHide);
                if (showing) {
                    hideRibbonView();
                }
            }
        }
    }
}
