/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import static com.android.systemui.statusbar.phone.StatusBar.reinflateSignalCluster;

import android.annotation.Nullable;
import android.app.Fragment;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.phone.TickerView;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";
    private PhoneStatusBarView mStatusBar;
    private KeyguardMonitor mKeyguardMonitor;
    private NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private View mNotificationIconAreaInner;
    private int mDisabled1;
    private StatusBar mStatusBarComponent;
    private DarkIconManager mDarkIconManager;
    private SignalClusterView mSignalClusterView;

    // Citrus additions start
    private ImageView mCustomLogo; 
    private int mCustomlogoStyle; 
    private View mTickerViewFromStub;    
    private boolean mShowCustomLogo;    
    private final Handler mHandler = new Handler();
    private int mTickerEnabled;
    private ContentResolver mContentResolver;
    // Custom Carrier
    private View mCustomCarrierLabel;
    private int mShowCarrierLabel;

    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CUSTOM_LOGO),
                    false, this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CUSTOM_LOGO_STYLE),
                    false, this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_SHOW_TICKER),
                    false, this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_CARRIER),
                    false, this, UserHandle.USER_ALL);
                    
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings(true);
        }
    }

    private CustomSettingsObserver mCustomSettingsObserver;
    
    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(NetworkController.IconState icon) {
            mStatusBarComponent.recomputeDisableFlags(true /* animate */);
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getContext().getContentResolver();
        mKeyguardMonitor = Dependency.get(KeyguardMonitor.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mStatusBarComponent = SysUiServiceProvider.getComponent(getContext(), StatusBar.class);
        mCustomSettingsObserver = new CustomSettingsObserver(mHandler); 
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.status_bar, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBar = (PhoneStatusBarView) view;
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.go(savedInstanceState.getInt(EXTRA_PANEL_STATE));
        }
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons));
        Dependency.get(StatusBarIconController.class).addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mSignalClusterView = mStatusBar.findViewById(R.id.signal_cluster);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mSignalClusterView);
        mCustomLogo = mStatusBar.findViewById(R.id.status_bar_custom_logo);
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mCustomLogo);      
        mCustomSettingsObserver.observe();
        mCustomCarrierLabel = mStatusBar.findViewById(R.id.statusbar_carrier_text);        
        updateSettings(false);
        // initTickerView() already called above from updateSettings(false) 
        // Default to showing until we know otherwise.
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_PANEL_STATE, mStatusBar.getState());
    }

    @Override
    public void onResume() {
        super.onResume();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).addCallbacks(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).removeCallbacks(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mSignalClusterView);
        Dependency.get(StatusBarIconController.class).removeIconGroup(mDarkIconManager);
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mCustomLogo);        
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
    }

    public void initNotificationIconArea(NotificationIconAreaController
            notificationIconAreaController) {
        ViewGroup notificationIconArea = mStatusBar.findViewById(R.id.notification_icon_area);
        mNotificationIconAreaInner =
                notificationIconAreaController.getNotificationInnerAreaView();
        if (mNotificationIconAreaInner.getParent() != null) {
            ((ViewGroup) mNotificationIconAreaInner.getParent())
                    .removeView(mNotificationIconAreaInner);
        }
        notificationIconArea.addView(mNotificationIconAreaInner);
        // Default to showing until we know otherwise.
        showNotificationIconArea(false);
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        state1 = adjustDisableFlags(state1);
        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        mDisabled1 = state1;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0) {
                hideSystemIconArea(animate);
            } else {
                showSystemIconArea(animate);
            }
        }
        if ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0) {
            if ((state1 & DISABLE_NOTIFICATION_ICONS) != 0) {
                hideNotificationIconArea(animate);
                hideCarrierName(animate);
            } else {
                showNotificationIconArea(animate);
                showCarrierName(animate);
            }
        }
    }

    protected int adjustDisableFlags(int state) {
        if (!mStatusBarComponent.isLaunchTransitionFadingAway()
                && !mKeyguardMonitor.isKeyguardFadingAway()
                && shouldHideNotificationIcons()) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
        }
        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
        }
        return state;
    }

    private boolean shouldHideNotificationIcons() {
        if (!mStatusBar.isClosed() && mStatusBarComponent.hideStatusBarIconsWhenExpanded()) {
            return true;
        }
        if (mStatusBarComponent.hideStatusBarIconsForBouncer()) {
            return true;
        }
        return false;
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate, true);
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate, true);
        if (mShowCustomLogo) {
            animateHide(mCustomLogo, animate, true);
        }
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);
        if (mShowCustomLogo) {
            animateShow(mCustomLogo, animate);            
        }
    }

    public void hideCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            animateHide(mCustomCarrierLabel, animate, false);
        }
    }

    public void showCarrierName(boolean animate) {
        if (mCustomCarrierLabel != null) {
            setCarrierLabel(animate);
        }
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate, final boolean invisible) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(invisible ? View.INVISIBLE : View.GONE);
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(invisible ? View.INVISIBLE : View.GONE));
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(50)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mKeyguardMonitor.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mKeyguardMonitor.getKeyguardFadingAwayDuration())
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .setStartDelay(mKeyguardMonitor.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    private void initEmergencyCryptkeeperText() {
        View emergencyViewStub = mStatusBar.findViewById(R.id.emergency_cryptkeeper_text);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            if (emergencyViewStub != null) {
                ((ViewStub) emergencyViewStub).inflate();
            }
            mNetworkController.addCallback(mSignalCallback);
        } else if (emergencyViewStub != null) {
            ViewGroup parent = (ViewGroup) emergencyViewStub.getParent();
            parent.removeView(emergencyViewStub);
        }
    }

    private void initTickerView() {
        if (mTickerEnabled != 0) {
            View tickerStub = mStatusBar.findViewById(R.id.ticker_stub);
            if (mTickerViewFromStub == null && tickerStub != null) {
                mTickerViewFromStub = ((ViewStub) tickerStub).inflate();
            }
            TickerView tickerView = (TickerView) mStatusBar.findViewById(R.id.tickerText);
            ImageSwitcher tickerIcon = (ImageSwitcher) mStatusBar.findViewById(R.id.tickerIcon);
            mStatusBarComponent.createTicker(
                    mTickerEnabled, getContext(), mStatusBar, tickerView, tickerIcon, mTickerViewFromStub);
        } else {
            mStatusBarComponent.disableTicker();
        }
    }
    
    public void updateSettings(boolean animate) {
        if (getContext() == null) {
            return;
        }

        mShowCustomLogo = Settings.System.getIntForUser(mContentResolver,
                Settings.System.STATUS_BAR_CUSTOM_LOGO, 0,
                UserHandle.USER_CURRENT) == 1;
        if (mNotificationIconAreaInner != null) {
            if (mShowCustomLogo) {
                if (mNotificationIconAreaInner.getVisibility() == View.VISIBLE) {
                    animateShow(mCustomLogo, animate);
                }
            } else {
                animateHide(mCustomLogo, animate, false);
        }
    }
    
        mTickerEnabled = Settings.System.getIntForUser(mContentResolver,
                Settings.System.STATUS_BAR_SHOW_TICKER, 0,
                UserHandle.USER_CURRENT);
        initTickerView();

        mCustomlogoStyle = Settings.System.getIntForUser(mContentResolver,
                Settings.System.STATUS_BAR_CUSTOM_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);
        updateCustomLogo();

        mShowCarrierLabel = Settings.System.getIntForUser(mContentResolver,
                Settings.System.STATUS_BAR_CARRIER, 1,
                UserHandle.USER_CURRENT);
        setCarrierLabel(animate);
    }

    private void setCarrierLabel(boolean animate) {
        if (mShowCarrierLabel == 2 || mShowCarrierLabel == 3) {
            animateShow(mCustomCarrierLabel, animate);
        } else {
            animateHide(mCustomCarrierLabel, animate, false);
        }
    }


    public void updateCustomLogo() {
        if (getContext() == null) {
            return;
        }

        Drawable d = null;
        
        if (!mShowCustomLogo) {
            mCustomLogo.setImageDrawable(null);
            mCustomLogo.setVisibility(View.GONE);
            return;
        } else {
            mCustomLogo.setVisibility(View.VISIBLE);            
        }

        int style = mCustomlogoStyle;
        if ( style == 0) {
            d = getContext().getResources().getDrawable(R.drawable.status_bar_logo);
        } else if ( style == 1) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_cardinal);
        } else if ( style == 2) {
            d = getContext().getResources().getDrawable(R.drawable.bugdroid);
        } else if ( style == 3) {
            d = getContext().getResources().getDrawable(R.drawable.drupal);
        } else if ( style == 4) {
            d = getContext().getResources().getDrawable(R.drawable.cool_smile);
        } else if ( style == 5) {
            d = getContext().getResources().getDrawable(R.drawable.ghost);
        } else if ( style == 6) {
            d = getContext().getResources().getDrawable(R.drawable.windows);
        } else if ( style == 7) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_pcmr);
        } else if ( style == 8) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_playstation);
        } else if ( style == 9) {
            d = getContext().getResources().getDrawable(R.drawable.xbox);
        } else if ( style == 10) {
            d = getContext().getResources().getDrawable(R.drawable.emoji);
        } else if ( style == 11) {
            d = getContext().getResources().getDrawable(R.drawable.happy_smile);
        } else if ( style == 12) {
            d = getContext().getResources().getDrawable(R.drawable.blender);
        } else if ( style == 13) {
            d = getContext().getResources().getDrawable(R.drawable.guitar_electric);
        } else if ( style == 14) {
            d = getContext().getResources().getDrawable(R.drawable.radioactive);
        } else if ( style == 15) {
            d = getContext().getResources().getDrawable(R.drawable.professional_hexagon);
        } else if ( style == 16) {
            d = getContext().getResources().getDrawable(R.drawable.pokeball);
        } else if ( style == 17) {
            d = getContext().getResources().getDrawable(R.drawable.cat);
        } else if ( style == 18) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_batman);
        } else if ( style == 19) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_batman_2k16);
        } else if ( style == 20) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_captain_america);
        } else if ( style == 21) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_daredevil);
        } else if ( style == 22) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_deadpool);
        } else if ( style == 23) {
            d = getContext().getResources().getDrawable(R.drawable.flash);
        } else if ( style == 24) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_fsociety);
        } else if ( style == 25) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_ironman);
        } else if ( style == 26) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_justice_league);
        } else if ( style == 27) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_green_lantern);
        } else if ( style == 28) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_punisher);
        } else if ( style == 29) {
            d = getContext().getResources().getDrawable(R.drawable.spider1);
        } else if ( style == 30) {
            d = getContext().getResources().getDrawable(R.drawable.spider2);
        } else if ( style == 31) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_superman);
        } else if ( style == 32) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_wonder_women);
        } else if ( style == 33) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_ac_milan);
        } else if ( style == 34) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_barcelona);
        } else if ( style == 35) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_bayern);
        } else if ( style == 36) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_chelsea);
        } else if ( style == 37) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_juventus);
        } else if ( style == 38) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_liverpool);
        } else if ( style == 39) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_man_city);
        } else if ( style == 40) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_man_utd);
        } else if ( style == 41) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_real_madrid);
        } else if ( style == 42) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_tottenham);
        } else if ( style == 43) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_mi);
        } else if ( style == 44) {
            d = getContext().getResources().getDrawable(R.drawable.ic_statusbar_logo_oneplus);

    }
	    mCustomLogo.setImageDrawable(null);
	    mCustomLogo.setImageDrawable(d);
   }
}
