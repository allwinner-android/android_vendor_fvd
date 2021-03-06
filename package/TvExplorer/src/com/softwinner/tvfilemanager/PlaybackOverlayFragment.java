/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.softwinner.tvfilemanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.ControlButtonPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRow.FastForwardAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.PlayPauseAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.RepeatAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.RewindAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.ShuffleAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.SkipNextAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.SkipPreviousAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.ThumbsDownAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.ThumbsUpAction;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.util.Log;
import android.view.KeyEvent;

import com.softwinner.tvfilemanager.entity.LocalVideo;
import com.softwinner.tvfilemanager.presenter.VideoCardPresenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/*
 * Class for LocalVideo playback with media control
 */
public class PlaybackOverlayFragment extends android.support.v17.leanback.app.PlaybackOverlayFragment  {
    private static final String TAG = "PlaybackOverlayFragment";
    private static final boolean SHOW_DETAIL = true;
    private static final boolean HIDE_MORE_ACTIONS = false;
    private static final int PRIMARY_CONTROLS = 5;
    private static final boolean SHOW_IMAGE = PRIMARY_CONTROLS <= 5;
    private static final int BACKGROUND_TYPE = PlaybackOverlayFragment.BG_LIGHT;
    private static final int CARD_WIDTH = 200;
    private static final int CARD_HEIGHT = 240;
    private static final int DEFAULT_UPDATE_PERIOD = 1000;
    private static final int UPDATE_PERIOD = 16;
    private static final int SIMULATED_BUFFERED_TIME = 10000;
    private static final int CLICK_TRACKING_DELAY = 1000;
    private static final int INITIAL_SPEED = 10000;

    private static Context sContext;
    private ImageLoadManager imageLoadManager;
    private final Handler mClickTrackingHandler = new Handler();
    OnPlayPauseClickedListener mCallback;
    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mPrimaryActionsAdapter;
    private ArrayObjectAdapter mSecondaryActionsAdapter;
    private PlayPauseAction mPlayPauseAction;
    private RepeatAction mRepeatAction;
    private ThumbsUpAction mThumbsUpAction;
    private ThumbsDownAction mThumbsDownAction;
    private ShuffleAction mShuffleAction;
    private FastForwardAction mFastForwardAction;
    private RewindAction mRewindAction;
    private SkipNextAction mSkipNextAction;
    private SkipPreviousAction mSkipPreviousAction;
    private PlaybackControlsRow mPlaybackControlsRow;
    private ArrayList<LocalVideo> mItems = new ArrayList<LocalVideo>();
    private int mCurrentItem = 7;
    private long mDuration;
    private Handler mHandler;
    private Runnable mRunnable;
    private LocalVideo mSelectedMovie;
    private int mFfwRwdSpeed = INITIAL_SPEED;
    private Timer mClickTrackingTimer;
    private int mClickCount;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        sContext = getActivity();
        imageLoadManager = ImageLoadManager.getInstance(sContext);
        Log.e("ImageLoadManager" , "playfragment: " + imageLoadManager);

        mItems = new ArrayList<LocalVideo>();
        mSelectedMovie = (LocalVideo) getActivity()
                .getIntent().getParcelableExtra(VideoDetailsActivity.VIDEO);


        final List<LocalVideo> localVideos = MyApplication.mLocalVideo;
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new VideoCardPresenter());

        for (int j = 0; j < localVideos.size(); j++) {
            mItems.add(localVideos.get(j));
            if (mSelectedMovie.getFile_name().contentEquals(localVideos.get(j).getFile_name())) {
                mCurrentItem = j;
            }
        }

        mHandler = new Handler();

        setBackgroundType(BACKGROUND_TYPE);
        setFadingEnabled(false);

        setupRows();

        setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                       RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemSelected: " + item + " row " + row);
            }
        });
        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemClicked: " + item + " row " + row);
                if (item instanceof LocalVideo) {
                    LocalVideo video = (LocalVideo) item;
                    Intent intent = new Intent(getActivity(), PlaybackOverlayActivity.class);
                    intent.putExtra(VideoDetailsActivity.VIDEO, video);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();

                    getActivity().startActivity(intent, bundle);
                }
            }
        });
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mCallback = (OnPlayPauseClickedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnPlayPauseClickedListener");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setupRows() {

        ClassPresenterSelector ps = new ClassPresenterSelector();

        PlaybackControlsRowPresenter playbackControlsRowPresenter;
        if (SHOW_DETAIL) {
            playbackControlsRowPresenter = new PlaybackControlsRowPresenter(
                    new DescriptionPresenter());
        } else {
            playbackControlsRowPresenter = new PlaybackControlsRowPresenter();
        }
        playbackControlsRowPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            public void onActionClicked(Action action) {
                if (action.getId() == mPlayPauseAction.getId()) {
                    if (mPlayPauseAction.getIndex() == PlayPauseAction.PLAY) {
                        startProgressAutomation();
                        setFadingEnabled(true);
                        mCallback.onFragmentPlayPause(mItems.get(mCurrentItem),
                                mPlaybackControlsRow.getCurrentTime(), true);
                    } else {
                        stopProgressAutomation();
                        setFadingEnabled(false);
                        mCallback.onFragmentPlayPause(mItems.get(mCurrentItem),
                                mPlaybackControlsRow.getCurrentTime(), false);
                    }
                } else if (action.getId() == mSkipNextAction.getId()) {
                    next();
                } else if (action.getId() == mSkipPreviousAction.getId()) {
                    prev();
                } else if (action.getId() == mFastForwardAction.getId()) {
                    fastForward();
                } else if (action.getId() == mRewindAction.getId()) {
                    fastRewind();
                }
                if (action instanceof PlaybackControlsRow.MultiAction) {
                    ((PlaybackControlsRow.MultiAction) action).nextIndex();
                    notifyChanged(action);
                }
            }
        });
        playbackControlsRowPresenter.setSecondaryActionsHidden(HIDE_MORE_ACTIONS);

        ps.addClassPresenter(PlaybackControlsRow.class, playbackControlsRowPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(ps);

        addPlaybackControlsRow();
        addOtherRows();

        setAdapter(mRowsAdapter);
    }

    private int getDuration() {
        LocalVideo LocalVideo = mItems.get(mCurrentItem);
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        /*    mmr.setDataSource(LocalVideo.getLocalVideo_path(), new HashMap<String, String>());
        } else {*/
            try {
                mmr.setDataSource(LocalVideo.getFile_path());
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }
        }
        String time = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        mDuration = Long.parseLong(time);
        return (int) mDuration;
    }

    private void addPlaybackControlsRow() {
        if (SHOW_DETAIL) {
            mPlaybackControlsRow = new PlaybackControlsRow(mSelectedMovie);
        } else {
            mPlaybackControlsRow = new PlaybackControlsRow();
        }
        mRowsAdapter.add(mPlaybackControlsRow);

        updatePlaybackRow(mCurrentItem);

        ControlButtonPresenterSelector presenterSelector = new ControlButtonPresenterSelector();
        mPrimaryActionsAdapter = new ArrayObjectAdapter(presenterSelector);
        mSecondaryActionsAdapter = new ArrayObjectAdapter(presenterSelector);
        mPlaybackControlsRow.setPrimaryActionsAdapter(mPrimaryActionsAdapter);
        mPlaybackControlsRow.setSecondaryActionsAdapter(mSecondaryActionsAdapter);

        mPlayPauseAction = new PlayPauseAction(sContext);
        mRepeatAction = new RepeatAction(sContext);
        mThumbsUpAction = new ThumbsUpAction(sContext);
        mThumbsDownAction = new ThumbsDownAction(sContext);
        mShuffleAction = new ShuffleAction(sContext);
        mSkipNextAction = new PlaybackControlsRow.SkipNextAction(sContext);
        mSkipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(sContext);
        mFastForwardAction = new PlaybackControlsRow.FastForwardAction(sContext);
        mRewindAction = new PlaybackControlsRow.RewindAction(sContext);

        if (PRIMARY_CONTROLS > 5) {
            mPrimaryActionsAdapter.add(mThumbsUpAction);
        } else {
            mSecondaryActionsAdapter.add(mThumbsUpAction);
        }
        mPrimaryActionsAdapter.add(mSkipPreviousAction);
        if (PRIMARY_CONTROLS > 3) {
            mPrimaryActionsAdapter.add(new PlaybackControlsRow.RewindAction(sContext));
        }
        mPrimaryActionsAdapter.add(mPlayPauseAction);
        if (PRIMARY_CONTROLS > 3) {
            mPrimaryActionsAdapter.add(new PlaybackControlsRow.FastForwardAction(sContext));
        }
        mPrimaryActionsAdapter.add(mSkipNextAction);

        mSecondaryActionsAdapter.add(mRepeatAction);
        mSecondaryActionsAdapter.add(mShuffleAction);
        if (PRIMARY_CONTROLS > 5) {
            mPrimaryActionsAdapter.add(mThumbsDownAction);
        } else {
            mSecondaryActionsAdapter.add(mThumbsDownAction);
        }
        mSecondaryActionsAdapter.add(new PlaybackControlsRow.HighQualityAction(sContext));
        mSecondaryActionsAdapter.add(new PlaybackControlsRow.ClosedCaptioningAction(sContext));
    }

    private void notifyChanged(Action action) {
        ArrayObjectAdapter adapter = mPrimaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
            return;
        }
        adapter = mSecondaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
            return;
        }
    }

    private void updatePlaybackRow(int index) {
        if (mPlaybackControlsRow.getItem() != null) {
            LocalVideo item = (LocalVideo) mPlaybackControlsRow.getItem();
            item.setFile_name(mItems.get(mCurrentItem).getFile_name());
        }
        if (SHOW_IMAGE) {
            if(null != imageLoadManager.getBitmapFromMemoryCache(mItems.get(mCurrentItem).
                    getFile_path())) {
                updateLocalVideoImage(imageLoadManager.getBitmapFromMemoryCache(mItems.get(mCurrentItem).
                        getFile_path()).getBitmap());
            }
        }
        mRowsAdapter.notifyArrayItemRangeChanged(0, 1);
        mPlaybackControlsRow.setTotalTime(getDuration());
        mPlaybackControlsRow.setCurrentTime(0);
        mPlaybackControlsRow.setBufferedProgress(0);
    }

    private void addOtherRows() {
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new VideoCardPresenter());
        for (LocalVideo LocalVideo : mItems) {
            listRowAdapter.add(LocalVideo);
        }
        HeaderItem header = new HeaderItem(0, getString(R.string.related_movies), null);
        mRowsAdapter.add(new ListRow(header, listRowAdapter));

    }

    private int getUpdatePeriod() {
        if (getView() == null || mPlaybackControlsRow.getTotalTime() <= 0) {
            return DEFAULT_UPDATE_PERIOD;
        }
        return Math.max(UPDATE_PERIOD, mPlaybackControlsRow.getTotalTime() / getView().getWidth());
    }

    private void startProgressAutomation() {
        mRunnable = new Runnable() {
            @Override
            public void run() {
                int updatePeriod = getUpdatePeriod();
                int currentTime = mPlaybackControlsRow.getCurrentTime() + updatePeriod;
                int totalTime = mPlaybackControlsRow.getTotalTime();
                mPlaybackControlsRow.setCurrentTime(currentTime);
                mPlaybackControlsRow.setBufferedProgress(currentTime + SIMULATED_BUFFERED_TIME);

                if (totalTime > 0 && totalTime <= currentTime) {
                    next();
                }
                mHandler.postDelayed(this, updatePeriod);
            }
        };
        mHandler.postDelayed(mRunnable, getUpdatePeriod());
    }

    private void next() {
        if (++mCurrentItem >= mItems.size()) {
            mCurrentItem = 0;
        }
        if (mPlayPauseAction.getIndex() == PlayPauseAction.PLAY) {
            mCallback.onFragmentPlayPause(mItems.get(mCurrentItem), 0, false);
        } else {
            mCallback.onFragmentPlayPause(mItems.get(mCurrentItem), 0, true);
        }
        mFfwRwdSpeed = INITIAL_SPEED;
        updatePlaybackRow(mCurrentItem);
    }

    private void prev() {
        if (--mCurrentItem < 0) {
            mCurrentItem = mItems.size() - 1;
        }
        if (mPlayPauseAction.getIndex() == PlayPauseAction.PLAY) {
            mCallback.onFragmentPlayPause(mItems.get(mCurrentItem), 0, false);
        } else {
            mCallback.onFragmentPlayPause(mItems.get(mCurrentItem), 0, true);
        }
        mFfwRwdSpeed = INITIAL_SPEED;
        updatePlaybackRow(mCurrentItem);
    }

    private void fastForward() {
        Log.d(TAG, "current time: " + mPlaybackControlsRow.getCurrentTime());
        startClickTrackingTimer();
        int currentTime = mPlaybackControlsRow.getCurrentTime() + mFfwRwdSpeed;
        if (currentTime > (int) mDuration) {
            currentTime = (int) mDuration;
        }
        fastFR(currentTime);
    }

    private void fastRewind() {
        startClickTrackingTimer();
        int currentTime = mPlaybackControlsRow.getCurrentTime() - mFfwRwdSpeed;
        if (currentTime < 0 || currentTime > (int) mDuration) {
            currentTime = 0;
        }
        fastFR(currentTime);
    }

    private void fastFR(int currentTime) {
        mCallback.onFragmentFfwRwd(mItems.get(mCurrentItem), currentTime);
        mPlaybackControlsRow.setCurrentTime(currentTime);
        mPlaybackControlsRow.setBufferedProgress(currentTime + SIMULATED_BUFFERED_TIME);
    }

    private void stopProgressAutomation() {
        if (mHandler != null && mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
        }
    }

    @Override
    public void onStop() {
        stopProgressAutomation();
        super.onStop();
    }

    protected void updateLocalVideoImage(Bitmap bitmap) {

        Bitmap resizeBmp = null;
        if (null != bitmap) {
            Matrix matrix = new Matrix();
            matrix.postScale(0.8f, 1f); //??????????????????????????????
            resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        mPlaybackControlsRow.setImageDrawable(new BitmapDrawable(resizeBmp));
        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
    }

    private void startClickTrackingTimer() {
        if (null != mClickTrackingTimer) {
            mClickCount++;
            mClickTrackingTimer.cancel();
        } else {
            mClickCount = 0;
            mFfwRwdSpeed = INITIAL_SPEED;
        }
        mClickTrackingTimer = new Timer();
        mClickTrackingTimer.schedule(new UpdateFfwRwdSpeedTask(), CLICK_TRACKING_DELAY);
    }

    private  boolean isConsumableKey(KeyEvent keyEvent) {
        if (keyEvent.isSystem()) {
            return false;
        }
        return true;
    }

    /*@Override
    public void tickle() {
        if(this.is)
        super.tickle();
    }*/

    // Container Activity must implement this interface
    public interface OnPlayPauseClickedListener {
        public void onFragmentPlayPause(LocalVideo LocalVideo, int position, Boolean playPause);

        public void onFragmentFfwRwd(LocalVideo LocalVideo, int position);
    }

    static class DescriptionPresenter extends AbstractDetailsDescriptionPresenter {
        @Override
        protected void onBindDescription(ViewHolder viewHolder, Object item) {
            viewHolder.getTitle().setText(((LocalVideo) item).getFile_name());
            viewHolder.getSubtitle().setText(((LocalVideo) item).getFile_name());
        }
    }

    private class UpdateFfwRwdSpeedTask extends TimerTask {

        @Override
        public void run() {
            mClickTrackingHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mClickCount == 0) {
                        mFfwRwdSpeed = INITIAL_SPEED;
                    } else if (mClickCount == 1) {
                        mFfwRwdSpeed *= 2;
                    } else if (mClickCount >= 2) {
                        mFfwRwdSpeed *= 4;
                    }
                    mClickCount = 0;
                    mClickTrackingTimer = null;
                }
            });
        }
    }


}
