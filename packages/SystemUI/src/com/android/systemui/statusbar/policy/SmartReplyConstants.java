/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import static com.android.systemui.Dependency.MAIN_HANDLER_NAME;

import android.app.RemoteInput;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.KeyValueListParser;
import android.util.Log;

import com.android.systemui.R;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public final class SmartReplyConstants extends ContentObserver {

    private static final String TAG = "SmartReplyConstants";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_REQUIRES_TARGETING_P = "requires_targeting_p";
    private static final String KEY_MAX_SQUEEZE_REMEASURE_ATTEMPTS =
            "max_squeeze_remeasure_attempts";
    private static final String KEY_EDIT_CHOICES_BEFORE_SENDING =
            "edit_choices_before_sending";
    private static final String KEY_SHOW_IN_HEADS_UP = "show_in_heads_up";
    private static final String KEY_MIN_NUM_REPLIES = "min_num_system_generated_replies";
    private static final String KEY_MAX_NUM_ACTIONS = "max_num_actions";

    private final boolean mDefaultEnabled;
    private final boolean mDefaultRequiresP;
    private final int mDefaultMaxSqueezeRemeasureAttempts;
    private final boolean mDefaultEditChoicesBeforeSending;
    private final boolean mDefaultShowInHeadsUp;
    private final int mDefaultMinNumSystemGeneratedReplies;
    private final int mDefaultMaxNumActions;

    // These fields are updated on the UI thread but can be accessed on both the UI thread and
    // background threads. We use the volatile keyword here instead of synchronization blocks since
    // we only care about variable updates here being visible to other threads (and not for example
    // whether the variables we are reading were updated in the same go).
    private volatile boolean mEnabled;
    private volatile boolean mRequiresTargetingP;
    private volatile int mMaxSqueezeRemeasureAttempts;
    private volatile boolean mEditChoicesBeforeSending;
    private volatile boolean mShowInHeadsUp;
    private volatile int mMinNumSystemGeneratedReplies;
    private volatile int mMaxNumActions;

    private final Context mContext;
    private final KeyValueListParser mParser = new KeyValueListParser(',');

    @Inject
    public SmartReplyConstants(@Named(MAIN_HANDLER_NAME) Handler handler, Context context) {
        super(handler);

        mContext = context;
        final Resources resources = mContext.getResources();
        mDefaultEnabled = resources.getBoolean(
                R.bool.config_smart_replies_in_notifications_enabled);
        mDefaultRequiresP = resources.getBoolean(
                R.bool.config_smart_replies_in_notifications_requires_targeting_p);
        mDefaultMaxSqueezeRemeasureAttempts = resources.getInteger(
                R.integer.config_smart_replies_in_notifications_max_squeeze_remeasure_attempts);
        mDefaultEditChoicesBeforeSending = resources.getBoolean(
                R.bool.config_smart_replies_in_notifications_edit_choices_before_sending);
        mDefaultShowInHeadsUp = resources.getBoolean(
                R.bool.config_smart_replies_in_notifications_show_in_heads_up);
        mDefaultMinNumSystemGeneratedReplies = resources.getInteger(
                R.integer.config_smart_replies_in_notifications_min_num_system_generated_replies);
        mDefaultMaxNumActions = resources.getInteger(
                R.integer.config_smart_replies_in_notifications_max_num_actions);

        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.SMART_REPLIES_IN_NOTIFICATIONS_FLAGS),
                false, this);
        updateConstants();
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        updateConstants();
    }

    private void updateConstants() {
        synchronized (SmartReplyConstants.this) {
            try {
                mParser.setString(Settings.Global.getString(mContext.getContentResolver(),
                        Settings.Global.SMART_REPLIES_IN_NOTIFICATIONS_FLAGS));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Bad smart reply constants", e);
            }
            mEnabled = mParser.getBoolean(KEY_ENABLED, mDefaultEnabled);
            mRequiresTargetingP = mParser.getBoolean(KEY_REQUIRES_TARGETING_P, mDefaultRequiresP);
            mMaxSqueezeRemeasureAttempts = mParser.getInt(
                    KEY_MAX_SQUEEZE_REMEASURE_ATTEMPTS, mDefaultMaxSqueezeRemeasureAttempts);
            mEditChoicesBeforeSending = mParser.getBoolean(
                    KEY_EDIT_CHOICES_BEFORE_SENDING, mDefaultEditChoicesBeforeSending);
            mShowInHeadsUp = mParser.getBoolean(KEY_SHOW_IN_HEADS_UP, mDefaultShowInHeadsUp);
            mMinNumSystemGeneratedReplies =
                    mParser.getInt(KEY_MIN_NUM_REPLIES, mDefaultMinNumSystemGeneratedReplies);
            mMaxNumActions = mParser.getInt(KEY_MAX_NUM_ACTIONS, mDefaultMaxNumActions);
        }
    }

    /** Returns whether smart replies in notifications are enabled. */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns whether smart replies in notifications should be disabled when the app targets a
     * version of Android older than P.
     */
    public boolean requiresTargetingP() {
        return mRequiresTargetingP;
    }

    /**
     * Returns the maximum number of times {@link SmartReplyView#onMeasure(int, int)} will try to
     * find a better (narrower) line-break for a double-line smart reply button.
     */
    public int getMaxSqueezeRemeasureAttempts() {
        return mMaxSqueezeRemeasureAttempts;
    }

    /**
     * Returns whether by tapping on a choice should let the user edit the input before it
     * is sent to the app.
     *
     * @param remoteInputEditChoicesBeforeSending The value from
     *         {@link RemoteInput#getEditChoicesBeforeSending()}
     */
    public boolean getEffectiveEditChoicesBeforeSending(
            @RemoteInput.EditChoicesBeforeSending int remoteInputEditChoicesBeforeSending) {
        switch (remoteInputEditChoicesBeforeSending) {
            case RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED:
                return false;
            case RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED:
                return true;
            case RemoteInput.EDIT_CHOICES_BEFORE_SENDING_AUTO:
            default:
                return mEditChoicesBeforeSending;
        }
    }

    /**
     * Returns whether smart suggestions should be enabled in heads-up notifications.
     */
    public boolean getShowInHeadsUp() {
        return mShowInHeadsUp;
    }

    /**
     * Returns the minimum number of system generated replies to show in a notification.
     * If we cannot show at least this many system generated replies we should show none.
     */
    public int getMinNumSystemGeneratedReplies() {
        return mMinNumSystemGeneratedReplies;
    }

    /**
     * Returns the maximum number smart actions to show in a notification, or -1 if there shouldn't
     * be a limit.
     */
    public int getMaxNumActions() {
        return mMaxNumActions;
    }
}
