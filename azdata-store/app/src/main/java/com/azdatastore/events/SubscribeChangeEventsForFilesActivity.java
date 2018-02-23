package com.azdatastore.events;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.events.ChangeEvent;
import com.azdatastore.BaseDemoActivity;
import com.azdatastore.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Date;

public class SubscribeChangeEventsForFilesActivity extends BaseDemoActivity {
    private static final String TAG = "SubscribeChangeEvents";

    private Button mActionButton;

    private TextView mLogTextView;

    private DriveId mSelectedFileId;

    private boolean mIsSubscribed = false;

    protected BroadcastReceiver mBroadcastReceiver;

    private CountDownTimer mCountDownTimer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changeevents);

        mLogTextView = findViewById(R.id.textViewLog);
        mActionButton = findViewById(R.id.buttonAction);
        mActionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ChangeEvent event = intent.getParcelableExtra("event");
                mLogTextView.append(getString(R.string.change_event, event));
            }
        };
    }

    @Override
    protected void onDriveClientReady() {
        pickTextFile()
                .addOnSuccessListener(this,
                        new OnSuccessListener<DriveId>() {
                            @Override
                            public void onSuccess(DriveId driveId) {
                                mSelectedFileId = driveId;
                                refresh();
                            }
                        })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "No file selected", e);
                        showMessage(getString(R.string.file_not_selected));
                        finish();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mBroadcastReceiver, new IntentFilter(MyDriveEventService.CHANGE_EVENT));
    }

    @Override
    protected void onStop() {
        stopTimer();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onStop();
    }

    /**
     * Refreshes the status of UI elements. Enables/disables subscription button
     * depending on whether there is file picked by the user.
     */
    private void refresh() {
        if (mSelectedFileId == null) {
            mActionButton.setEnabled(false);
        } else {
            mActionButton.setEnabled(true);
        }

        if (!mIsSubscribed) {
            mActionButton.setText(R.string.button_subscribe);
        } else {
            mActionButton.setText(R.string.button_unsubscribe);
        }
    }

    private void toggle() {
        if (mSelectedFileId == null) {
            return;
        }
        stopTimer();
        DriveFile file = mSelectedFileId.asDriveFile();
        if (!mIsSubscribed) {
            Log.d(TAG, "Starting to listen to the file changes.");
            mIsSubscribed = true;
            mCountDownTimer = new TickleTimer(30000 /* 30 seconds total */,
                    1000 /* tick every 1 second */);
            mCountDownTimer.start();
            // [START add_change_subscription]
            getDriveResourceClient().addChangeSubscription(file).addOnSuccessListener(
                    new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            showMessage(getString(R.string.subscribed));
                        }
                    });
            // [END add_change_subscription]
        } else {
            Log.d(TAG, "Stopping to listen to the file changes.");
            mIsSubscribed = false;
            // [START remove_change_listener]
            getDriveResourceClient().removeChangeSubscription(file).addOnSuccessListener(
                    new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            showMessage(getString(R.string.unsubscribed));
                        }
                    });
            // [END remove_change_listener]
        }
        refresh();
    }

    private void stopTimer() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
    }

    private class TickleTimer extends CountDownTimer {
        TickleTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long l) {
            Log.d(TAG, "Updating metadata.");
            MetadataChangeSet metadata =
                    new MetadataChangeSet.Builder().setLastViewedByMeDate(new Date()).build();
            getDriveResourceClient()
                    .updateMetadata(mSelectedFileId.asDriveResource(), metadata)
                    .addOnSuccessListener(SubscribeChangeEventsForFilesActivity.this,
                            new OnSuccessListener<Metadata>() {
                                @Override
                                public void onSuccess(Metadata metadata) {
                                    Log.d(TAG, "Updated metadata.");
                                }
                            })
                    .addOnFailureListener(
                            SubscribeChangeEventsForFilesActivity.this, new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e(TAG, "Unable to update metadata", e);
                                }
                            });
        }

        @Override
        public void onFinish() {
            showMessage(getString(R.string.tickle_finished));
        }
    }
}
