/*
 * Copyright (c) 2016 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.douya.item.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.ObjectsCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.zhanghai.android.douya.R;
import me.zhanghai.android.douya.eventbus.EventBusUtils;
import me.zhanghai.android.douya.eventbus.ItemCollectErrorEvent;
import me.zhanghai.android.douya.eventbus.ItemCollectedEvent;
import me.zhanghai.android.douya.eventbus.ItemUncollectedEvent;
import me.zhanghai.android.douya.item.content.CollectItemManager;
import me.zhanghai.android.douya.item.content.ConfirmUncollectItemDialogFragment;
import me.zhanghai.android.douya.item.content.UncollectItemManager;
import me.zhanghai.android.douya.network.api.info.frodo.CollectableItem;
import me.zhanghai.android.douya.network.api.info.frodo.ItemCollectionState;
import me.zhanghai.android.douya.network.api.info.frodo.SimpleItemCollection;
import me.zhanghai.android.douya.ui.ConfirmDiscardContentDialogFragment;
import me.zhanghai.android.douya.ui.FragmentFinishable;
import me.zhanghai.android.douya.util.DoubanUtils;
import me.zhanghai.android.douya.util.FragmentUtils;
import me.zhanghai.android.douya.util.MoreTextUtils;
import me.zhanghai.android.douya.util.StringCompat;
import me.zhanghai.android.douya.util.ViewUtils;
import me.zhanghai.android.materialratingbar.MaterialRatingBar;

public class ItemCollectionFragment extends Fragment
        implements ConfirmUncollectItemDialogFragment.Listener,
        ConfirmDiscardContentDialogFragment.Listener {

    private static final String KEY_PREFIX = ItemCollectionFragment.class.getName() + '.';

    private static final String EXTRA_ITEM = KEY_PREFIX + "item";
    private static final String EXTRA_STATE = KEY_PREFIX + "state";

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.state_layout)
    ViewGroup mStateLayout;
    @BindView(R.id.state)
    Spinner mStateSpinner;
    @BindView(R.id.state_this_item)
    TextView mStateThisItemText;
    @BindView(R.id.rating_layout)
    ViewGroup mRatingLayout;
    @BindView(R.id.rating)
    MaterialRatingBar mRatingBar;
    @BindView(R.id.rating_hint)
    TextView mRatingHintText;
    @BindView(R.id.tags)
    EditText mTagsEdit;
    @BindView(R.id.comment)
    EditText mCommentEdit;

    private MenuItem mCollectMenuItem;
    private MenuItem mUncollectMenuItem;

    private CollectableItem mItem;
    private ItemCollectionState mExtraState;

    private boolean mCollectd;

    /**
     * @deprecated Use {@link #newInstance(CollectableItem, ItemCollectionState)} instead.
     */
    public ItemCollectionFragment() {}

    public static ItemCollectionFragment newInstance(CollectableItem item,
                                                     ItemCollectionState state) {
        //noinnspection deprecation
        ItemCollectionFragment fragment = new ItemCollectionFragment();
        Bundle arguments = FragmentUtils.ensureArguments(fragment);
        arguments.putParcelable(EXTRA_ITEM, item);
        arguments.putSerializable(EXTRA_STATE, state);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mItem = arguments.getParcelable(EXTRA_ITEM);
        mExtraState = (ItemCollectionState) arguments.getSerializable(EXTRA_STATE);

        setHasOptionsMenu(true);

        EventBusUtils.register(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.item_collection_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ButterKnife.bind(this, view);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        activity.setTitle(mItem.title);
        activity.setSupportActionBar(mToolbar);

        mStateLayout.setOnClickListener(view -> mStateSpinner.performClick());
        mStateSpinner.setAdapter(new ItemCollectionStateSpinnerAdapter(mItem.getType(),
                mStateSpinner.getContext()));
        if (savedInstanceState == null) {
            ItemCollectionState state;
            if (mExtraState != null) {
                state = mExtraState;
            } else if (mItem.collection != null) {
                state = mItem.collection.getState();
            } else {
                state = null;
            }
            if (state != null) {
                mStateSpinner.setSelection(state.ordinal(), false);
            }
        }
        mStateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onStateChanged();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        mStateThisItemText.setText(mItem.getType().getThisItem(activity));
        onStateChanged();
        if (savedInstanceState == null && mItem.collection != null
                && mItem.collection.rating != null) {
            mRatingBar.setRating(mItem.collection.rating.getRatingBarValue());
        }
        mRatingBar.setOnRatingChangeListener((ratingBar, rating) -> updateRatingHint());
        updateRatingHint();
        if (savedInstanceState == null && mItem.collection != null) {
            setTags(mItem.collection.tags);
        }
        if (savedInstanceState == null && mItem.collection != null) {
            mCommentEdit.setText(mItem.collection.comment);
        }

        updateCollectStatus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBusUtils.unregister(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.item_collection, menu);
        mCollectMenuItem = menu.findItem(R.id.action_collect);
        mUncollectMenuItem = menu.findItem(R.id.action_uncollect);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        updateOptionsMenu();
    }

    private void updateOptionsMenu() {
        if (mCollectMenuItem == null && mUncollectMenuItem == null) {
            return;
        }
        boolean showUncollect = mItem.collection != null && (mExtraState == null
                || getState() == mItem.collection.getState());
        mUncollectMenuItem.setVisible(showUncollect);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onFinish();
                return true;
            case R.id.action_uncollect:
                onUncollect();
                return true;
            case R.id.action_collect:
                collect();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onStateChanged() {
        boolean hasRating = getState() != ItemCollectionState.TODO;
        ViewUtils.setVisibleOrGone(mRatingLayout, hasRating);
        updateOptionsMenu();
    }

    private void updateRatingHint() {
        mRatingHintText.setText(DoubanUtils.getRatingHint((int) mRatingBar.getRating(),
                mRatingHintText.getContext()));
    }

    private void onUncollect() {
        ConfirmUncollectItemDialogFragment.show(this);
    }

    @Override
    public void uncollect() {
        UncollectItemManager.getInstance().write(mItem, getActivity());
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onItemUncolleted(ItemUncollectedEvent event) {

        if (event.isFromMyself(this)) {
            return;
        }

        if (event.itemType == mItem.getType() && event.itemId == mItem.id) {
            finish();
        }
    }

    private void collect() {
        ItemCollectionState state = getState();
        int rating = getRating();
        List<String> tags = getTags();
        String comment = mCommentEdit.getText().toString();
        // TODO
        CollectItemManager.getInstance().write(mItem.getType(), mItem.id, state, rating, tags,
                comment, null, false, false, false, getActivity());
        updateCollectStatus();
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onBroadcastSent(ItemCollectedEvent event) {

        if (event.isFromMyself(this)) {
            return;
        }

        if (event.itemType == mItem.getType() && event.itemId == mItem.id) {
            mCollectd = true;
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onItemCollectError(ItemCollectErrorEvent event) {

        if (event.isFromMyself(this)) {
            return;
        }

        if (event.itemType == mItem.getType() && event.itemId == mItem.id) {
            updateCollectStatus();
        }
    }

    private void updateCollectStatus() {
        if (mCollectd) {
            return;
        }
        CollectItemManager manager = CollectItemManager.getInstance();
        boolean sending = manager.isWriting(mItem);
        Activity activity = getActivity();
        activity.setTitle(sending ? getString(R.string.item_collection_title_saving_format,
                mItem.getType().getName(activity)) : mItem.title);
        boolean enabled = !sending;
        mStateLayout.setEnabled(enabled);
        mStateSpinner.setEnabled(enabled);
        mRatingBar.setIsIndicator(!enabled);
        mTagsEdit.setEnabled(enabled);
        mCommentEdit.setEnabled(enabled);
        if (mCollectMenuItem != null) {
            mCollectMenuItem.setEnabled(enabled);
        }
        if (sending) {
            mStateSpinner.setSelection(manager.getState(mItem).ordinal(), false);
            // FIXME
            mRatingBar.setRating(manager.getRating(mItem));
            setTags(manager.getTags(mItem));
            mCommentEdit.setText(manager.getComment(mItem));
        }
    }

    public void onFinish() {
        if (isChanged()) {
            ConfirmDiscardContentDialogFragment.show(this);
        } else {
            finish();
        }
    }

    private boolean isChanged() {
        SimpleItemCollection collection = mItem.collection;
        ItemCollectionState state = getState();
        if (collection != null) {
            boolean equalsExtraState = mExtraState != null && state == mExtraState;
            boolean equalsCollectionState = state == collection.getState();
            if (!(equalsExtraState || equalsCollectionState)) {
                return true;
            }
        }
        if (state != ItemCollectionState.TODO) {
            float originalRating = collection != null && collection.rating != null ?
                    collection.rating.getRatingBarValue() : 0;
            float rating = mRatingBar.getRating();
            if (rating != originalRating) {
                return true;
            }
        }
        List<String> tags = getTags();
        List<String> originalTags = collection != null ? collection.tags : Collections.emptyList();
        if (!ObjectsCompat.equals(tags, originalTags)) {
            return true;
        }
        String comment = mCommentEdit.getText().toString();
        String originalComment = collection != null ? MoreTextUtils.nullToEmpty(collection.comment)
                : "";
        if (!TextUtils.equals(comment, originalComment)) {
            return true;
        }
        return false;
    }

    @Override
    public void discardContent() {
        finish();
    }

    private void finish() {
        FragmentFinishable.finish(getActivity());
    }

    private ItemCollectionState getState() {
        return ItemCollectionState.values()[mStateSpinner.getSelectedItemPosition()];
    }

    private int getRating() {
        // TODO: We are assuming max is 5 here.
        return (int) mRatingBar.getRating();
    }

    private List<String> getTags() {
        String tagsText = mTagsEdit.getText().toString();
        Matcher matcher = Pattern.compile("\\S+").matcher(tagsText);
        List<String> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags;
    }

    private void setTags(List<String> tags) {
        mTagsEdit.setText(StringCompat.join(" ", tags));
    }
}
