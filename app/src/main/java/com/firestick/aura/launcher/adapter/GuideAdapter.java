package com.firestick.aura.launcher.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.FastGuide;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.RemoteImageLoader;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.manager.StreamAvailability;
import com.firestick.aura.launcher.model.IptvChannel;
import com.firestick.aura.launcher.ui.GuideText;
import com.firestick.aura.launcher.ui.Monogram;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rows of the Live TV guide. Each row is one focus stop: the channel tile, the program on air
 * with its progress, then the next two programs. Rows without a FAST schedule show the channel
 * name and whether its feed answered instead.
 */
public class GuideAdapter extends RecyclerView.Adapter<GuideAdapter.Holder> {
    public interface Listener {
        void onPlay(IptvChannel channel);

        void onFavorite(IptvChannel channel, boolean favorite);

        void onFocus(IptvChannel channel);

        /** Left from a row. Returns true when it moved focus. */
        boolean onLeft();

        /** Up from the first row. Returns true when it moved focus. */
        boolean onUpFromTop();
    }

    /** Partial rebind: only the clock moved, so only the program cells change. */
    private static final Object TICK = new Object();
    private static final int LOGO_WIDTH_DP = 72;

    private final List<IptvChannel> items = new ArrayList<>();
    private final PreferenceManager preferences;
    private final Listener listener;
    private DateFormat clock;

    public GuideAdapter(PreferenceManager preferences, Listener listener) {
        this.preferences = preferences;
        this.listener = listener;
        setHasStableIds(true);
    }

    /**
     * Replaces the rows. When most rows stay (online check results, a favorite), only the changes
     * are dispatched, so the other rows keep their feed state instead of flashing "Checking".
     * A new category or search replaces everything; stable IDs keep focus on a channel that stays.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<IptvChannel> next) {
        List<IptvChannel> fresh = next == null ? new ArrayList<>() : new ArrayList<>(next);
        clock = null;
        if (!keepsMostRows(fresh)) {
            items.clear();
            items.addAll(fresh);
            notifyDataSetChanged();
            return;
        }
        List<IptvChannel> previous = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return fresh.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).id == fresh.get(newPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                IptvChannel a = previous.get(oldPosition), b = fresh.get(newPosition);
                return a.isFavorite == b.isFavorite && a.name.equals(b.name) && a.logo.equals(b.logo)
                        && a.category.equals(b.category) && java.util.Objects.equals(a.channelId, b.channelId)
                        && a.streamUrls.equals(b.streamUrls);
            }
        }, false);
        items.clear();
        items.addAll(fresh);
        diff.dispatchUpdatesTo(this);
    }

    /** The diff costs grow with the differences; past half the rows a full rebind is cheaper. */
    private boolean keepsMostRows(List<IptvChannel> fresh) {
        if (items.isEmpty() || fresh.isEmpty()) return false;
        Set<Long> old = new HashSet<>(items.size() * 2);
        for (IptvChannel channel : items) old.add(channel.id);
        int kept = 0;
        for (IptvChannel channel : fresh) if (old.contains(channel.id)) kept++;
        return kept * 2 >= Math.max(items.size(), fresh.size());
    }

    /** Moves every row to the current minute: the program on air, its progress and the next two. */
    public void tick() {
        clock = null;
        if (!items.isEmpty()) notifyItemRangeChanged(0, items.size(), TICK);
    }

    public IptvChannel get(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    public int indexOf(long id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == id) return i;
        return -1;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guide_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        IptvChannel channel = items.get(position);
        if (payloads.contains(TICK) && holder.channel != null && holder.channel.id == channel.id) {
            holder.bindProgram();
        } else {
            holder.bind(channel);
        }
    }

    private DateFormat clock(Context context) {
        if (clock == null) clock = GuideText.clock(context);
        return clock;
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final TextView number, initials, nowTitle, nowMeta;
        private final TextView next1Time, next1Title, next2Time, next2Title;
        private final ImageView logo, star;
        private final ProgressBar progress;
        private final View next1, next2;
        IptvChannel channel;
        /** Feed check of a non-FAST row: not answered yet, then true, false or null (skipped). */
        private boolean checked;
        private Boolean reachable;

        Holder(View view) {
            super(view);
            number = view.findViewById(R.id.guideNumber);
            logo = view.findViewById(R.id.guideLogo);
            initials = view.findViewById(R.id.guideInitials);
            star = view.findViewById(R.id.guideStar);
            nowTitle = view.findViewById(R.id.guideNowTitle);
            nowMeta = view.findViewById(R.id.guideNowMeta);
            progress = view.findViewById(R.id.guideNowProgress);
            next1 = view.findViewById(R.id.guideNext1);
            next1Time = view.findViewById(R.id.guideNext1Time);
            next1Title = view.findViewById(R.id.guideNext1Title);
            next2 = view.findViewById(R.id.guideNext2);
            next2Time = view.findViewById(R.id.guideNext2Time);
            next2Title = view.findViewById(R.id.guideNext2Title);
            view.setOnFocusChangeListener((v, focused) -> {
                if (!focused || channel == null) return;
                SoundManager.playFocus(v, preferences);
                listener.onFocus(channel);
            });
            view.setOnClickListener(v -> {
                if (channel == null) return;
                SoundManager.playClick(v, preferences);
                listener.onPlay(channel);
            });
            view.setOnLongClickListener(v -> {
                if (channel == null) return false;
                SoundManager.playClick(v, preferences);
                listener.onFavorite(channel, !channel.isFavorite);
                return true;
            });
            // Left returns to the selected category. Right has nothing to reach: the program cells
            // are not focus stops, and the default search would jump to the assistant button.
            // Up from the first row goes to the search box above the program on air; the default
            // search measures from the centre of the whole row and lands on Refresh.
            view.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return listener.onLeft();
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && getBindingAdapterPosition() == 0) {
                    return listener.onUpFromTop();
                }
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
        }

        void bind(IptvChannel channel) {
            this.channel = channel;
            Context context = itemView.getContext();
            itemView.setTag("channel:" + channel.id);
            star.setVisibility(channel.isFavorite ? View.VISIBLE : View.GONE);

            FastGuide.Schedule schedule = FastGuide.schedule(channel.channelId);
            if (schedule != null && schedule.number > 0) {
                number.setText(String.valueOf(schedule.number));
                number.setVisibility(View.VISIBLE);
            } else {
                number.setVisibility(View.GONE);
            }

            String url = channel.logo;
            logo.setTag(url);
            logo.setImageDrawable(null);
            initials.setText(Monogram.initials(channel.name));
            initials.setVisibility(View.VISIBLE);
            if (!url.isEmpty()) {
                int width = Math.round(LOGO_WIDTH_DP * context.getResources().getDisplayMetrics().density);
                RemoteImageLoader.get(context).load(url, width, bitmap -> {
                    if (!url.equals(logo.getTag())) return;
                    logo.setImageBitmap(bitmap);
                    initials.setVisibility(View.GONE);
                });
            }

            checked = false;
            reachable = null;
            if (!FastGuide.isFast(channel)) {
                StreamAvailability.checkChannel(channel, result -> {
                    if (this.channel != channel) return;
                    checked = true;
                    reachable = result;
                    nowMeta.setText(availabilityMeta(itemView.getContext()));
                    describe();
                });
            }
            bindProgram();
        }

        /** Program cells only; called on every full bind and once a minute. */
        void bindProgram() {
            Context context = itemView.getContext();
            if (!FastGuide.isFast(channel)) {
                nowTitle.setText(channel.name);
                nowMeta.setText(availabilityMeta(context));
                progress.setVisibility(View.GONE);
                next1.setVisibility(View.GONE);
                next2.setVisibility(View.GONE);
                describe();
                return;
            }
            long now = System.currentTimeMillis();
            FastGuide.Schedule schedule = FastGuide.schedule(channel.channelId);
            int onAir = schedule == null ? -1 : schedule.indexAt(now);
            String title = onAir < 0 ? "" : schedule.title(onAir);
            nowTitle.setText(title.isEmpty() ? channel.name : title);
            if (onAir < 0) {
                nowMeta.setText(R.string.guide_live);
            } else {
                String left = GuideText.left(context, schedule, now);
                String range = GuideText.range(clock(context), schedule, onAir);
                nowMeta.setText(left.isEmpty() ? range : range + "  ·  " + left);
            }
            int percent = schedule == null ? -1 : schedule.progressPercent(now);
            progress.setVisibility(percent < 0 ? View.INVISIBLE : View.VISIBLE);
            if (percent >= 0) progress.setProgress(percent);
            int next = schedule == null ? -1 : onAir >= 0 ? onAir + 1 : schedule.nextIndex(now);
            bindNext(context, schedule, next, next1, next1Time, next1Title);
            bindNext(context, schedule, next < 0 ? -1 : next + 1, next2, next2Time, next2Title);
            describe();
        }

        private void bindNext(Context context, FastGuide.Schedule schedule, int index, View cell, TextView time, TextView title) {
            if (schedule == null || index < 0 || index >= schedule.size()) {
                cell.setVisibility(View.INVISIBLE);
                return;
            }
            cell.setVisibility(View.VISIBLE);
            time.setText(GuideText.time(clock(context), schedule.start(index)));
            title.setText(schedule.title(index));
        }

        /** "● Reachable  ·  News", with the feed state in its color. */
        private CharSequence availabilityMeta(Context context) {
            SpannableStringBuilder text = new SpannableStringBuilder(context.getString(!checked ? R.string.iptv_checking
                    : reachable == null ? R.string.iptv_not_checked
                    : reachable ? R.string.iptv_reachable : R.string.iptv_unavailable));
            int color = ContextCompat.getColor(context, reachable == null ? R.color.nova_text_muted
                    : reachable ? R.color.state_success : R.color.state_error);
            text.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!channel.category.isEmpty()) text.append("  ·  ").append(channel.category);
            return text;
        }

        private void describe() {
            Context context = itemView.getContext();
            StringBuilder text = new StringBuilder(channel.name);
            CharSequence title = nowTitle.getText();
            if (title.length() > 0 && !channel.name.contentEquals(title)) text.append(". ").append(title);
            CharSequence meta = nowMeta.getText();
            if (meta.length() > 0) text.append(". ").append(meta);
            text.append(". ").append(context.getString(channel.isFavorite ? R.string.iptv_remove_favorite
                    : R.string.iptv_add_favorite, channel.name));
            itemView.setContentDescription(text);
        }
    }
}
