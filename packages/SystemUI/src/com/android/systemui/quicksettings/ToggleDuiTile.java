package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ToggleDuiTile extends QuickSettingsTile {

    public ToggleDuiTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mLabel = context.getString(R.string.quick_settings_dui_label);
        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.UI_INVERTED_MODE, !getUiInvertedMode() ? 1 : 0);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_SETTINGS);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.Secure.getUriFor(Settings.Secure.UI_INVERTED_MODE)
                , this);
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if(getUiInvertedMode()){
            mDrawable = R.drawable.ic_qs_dui_on;
            mLabel = mContext.getString(R.string.quick_settings_dui_on_label); 
        }else{
            mDrawable = R.drawable.ic_qs_dui_off;
            mLabel = mContext.getString(R.string.quick_settings_dui_off_label); 
        }
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    private boolean getUiInvertedMode() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.UI_INVERTED_MODE, 0) == 1;
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
