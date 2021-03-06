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

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.softwinner.tvfilemanager.entity.LocalVideo;
import com.softwinner.tvfilemanager.presenter.DetailsDescriptionPresenter;
import com.softwinner.tvfilemanager.presenter.VideoCardPresenter;

import java.util.List;

/*
 * LeanbackDetailsFragment extends DetailsFragment, a Wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its meta plus related videos.
 */
public class VideoDetailsFragment extends android.support.v17.leanback.app.DetailsFragment {
    private static final String TAG = "DetailsFragment";

    private static final int ACTION_WATCH_TRAILER = 1;
    private static final int ACTION_RENT = 2;
    private static final int ACTION_BUY = 3;

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private static final int NO_NOTIFICATION = -1;

    private LocalVideo mSelectedMovie;

    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;
    private ImageLoadManager imageLoadManager;

    private BackgroundManager mBackgroundManager;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate DetailsFragment");
        super.onCreate(savedInstanceState);

        imageLoadManager = ImageLoadManager.getInstance(getActivity());
        Log.e("ImageLoadManager" , "videoDetail: " + imageLoadManager);
        prepareBackgroundManager();

        mSelectedMovie = (LocalVideo) getActivity().getIntent()
                .getParcelableExtra(VideoDetailsActivity.VIDEO);
        if (null != mSelectedMovie || checkGlobalSearchIntent()) {
            Log.d(TAG, "DetailsActivity movie: " + mSelectedMovie.toString());
            removeNotification(getActivity().getIntent()
                    .getIntExtra(VideoDetailsActivity.NOTIFICATION_ID, NO_NOTIFICATION));
            setupAdapter();
            setupDetailsOverviewRow();
            setupDetailsOverviewRowPresenter();
            setupMovieListRow();
            setupMovieListRowPresenter();
            mBackgroundManager.setDrawable(new BitmapDrawable(mSelectedMovie.getFile_thumbnail()));
            setOnItemViewClickedListener(new ItemViewClickedListener());
        } else {
            Toast.makeText(getActivity(),"not select a video", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeNotification(int notificationId) {
        if (notificationId != NO_NOTIFICATION) {
            NotificationManager notificationManager = (NotificationManager) getActivity()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    /*
     * Check if there is a global search intent
     */
    private boolean checkGlobalSearchIntent() {
        Intent intent = getActivity().getIntent();
        String intentAction = intent.getAction();
        String globalSearch = getString(R.string.global_search);
        if (globalSearch.equalsIgnoreCase(intentAction)) {
            Uri intentData = intent.getData();
            Log.d(TAG, "action: " + intentAction + " intentData:" + intentData);
            int selectedIndex = Integer.parseInt(intentData.getLastPathSegment());
            List<LocalVideo> videos = MyApplication.mLocalVideo;
            int movieTally = 0;
            if (videos == null) {
                return false;
            }
                for (LocalVideo video : videos) {
                    movieTally++;
                    if (selectedIndex == movieTally) {
                        mSelectedMovie = video;
                        return true;
                    }
                }

        }
        return false;
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mDefaultBackground = getResources().getDrawable(R.drawable.default_background);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }



    private void setupAdapter() {
        mPresenterSelector = new ClassPresenterSelector();
        mAdapter = new ArrayObjectAdapter(mPresenterSelector);
        setAdapter(mAdapter);
    }

    private void setupDetailsOverviewRow() {
        Log.d(TAG, "doInBackground: " + mSelectedMovie.toString());
        final DetailsOverviewRow row = new DetailsOverviewRow(mSelectedMovie);
        row.setImageDrawable(getResources().getDrawable(R.drawable.default_background));

        Bitmap resizeBmp = null;
        if(null != imageLoadManager.getBitmapFromMemoryCache(mSelectedMovie.getFile_path())) {
            resizeBmp = imageLoadManager.getBitmapFromMemoryCache(mSelectedMovie.getFile_path()).getBitmap();
        }
        if (null != resizeBmp) {
            resizeBmp = Bitmap.createScaledBitmap(resizeBmp, 480,480, true);
        }
        row.setImageDrawable(new BitmapDrawable(resizeBmp));
        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());

        row.addAction(new Action(ACTION_WATCH_TRAILER, getResources().getString(
                R.string.watch_trailer_1), getResources().getString(R.string.watch_trailer_2)));
        /*row.addAction(new Action(ACTION_RENT, getResources().getString(R.string.rent_1),
                getResources().getString(R.string.rent_2)));
        row.addAction(new Action(ACTION_BUY, getResources().getString(R.string.buy_1),
                getResources().getString(R.string.buy_2)));*/

        mAdapter.add(row);
    }

    private void setupDetailsOverviewRowPresenter() {
        // Set detail background and style.
        DetailsOverviewRowPresenter detailsPresenter =
                new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        detailsPresenter.setBackgroundColor(getResources().getColor(R.color.detail_background));
        detailsPresenter.setStyleLarge(true);

        // Hook up transition element.
        detailsPresenter.setSharedElementEnterTransition(getActivity(),
                VideoDetailsActivity.SHARED_ELEMENT_NAME);

        detailsPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_WATCH_TRAILER) {

                    Intent intent = new Intent(getActivity(), PlaybackOverlayActivity.class);
                    intent.putExtra(VideoDetailsActivity.VIDEO, mSelectedMovie);
                    startActivity(intent);

                } else {
                    Toast.makeText(getActivity(), action.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void setupMovieListRow() {
        String subcategories[] = {getString(R.string.other_videos)};

        List<LocalVideo> videos = MyApplication.mLocalVideo;
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new VideoCardPresenter());
        for (LocalVideo video : videos) {
                    listRowAdapter.add(video);
        }
        HeaderItem header = new HeaderItem(0, subcategories[0], null);
        mAdapter.add(new ListRow(header, listRowAdapter));
    }

    private void setupMovieListRowPresenter() {
        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof LocalVideo) {
                LocalVideo video = (LocalVideo) item;
                Log.d(TAG, "Item: " + item.toString());
                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }


}
