package com.firestick.aura.launcher.adapter;

import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.model.AppCategory;
import com.firestick.aura.launcher.model.AppModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryRowAdapter extends RecyclerView.Adapter<CategoryRowAdapter.CategoryViewHolder> {
    private static final class Row {
        final String id;
        final String title;
        final int color;
        final List<AppModel> apps;
        Row(AppCategory category) {
            id = category.getId(); title = category.getTitle(); color = category.getAccentColor();
            apps = new ArrayList<>(category.getApps());
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, AppRowAdapter> adapters = new HashMap<>();
    private final Map<String, Parcelable> layoutStates = new HashMap<>();
    private final AppRowAdapter.OnAppActionListener listener;
    private final PreferenceManager preferences;
    private final RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();

    public CategoryRowAdapter(PreferenceManager preferences, AppRowAdapter.OnAppActionListener listener) {
        this.preferences = preferences;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setCategories(List<AppCategory> categories) {
        List<Row> next = new ArrayList<>();
        if (categories != null) for (AppCategory category : categories) next.add(new Row(category));
        List<Row> previous = new ArrayList<>(rows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return previous.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) { return previous.get(oldPos).id.equals(next.get(newPos).id); }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                Row old = previous.get(oldPos), fresh = next.get(newPos);
                if (!old.title.equals(fresh.title) || old.color != fresh.color || old.apps.size() != fresh.apps.size()) return false;
                for (int i = 0; i < old.apps.size(); i++)
                    if (!old.apps.get(i).getPackageName().equals(fresh.apps.get(i).getPackageName())) return false;
                return true;
            }
        });
        rows.clear(); rows.addAll(next); diff.dispatchUpdatesTo(this);
    }

    private void onAppFocused(String rowId, String packageName) {
        for (Map.Entry<String, AppRowAdapter> entry : adapters.entrySet())
            if (!entry.getKey().equals(rowId)) entry.getValue().clearFocusedApp();
    }

    @Override public long getItemId(int position) { return rows.get(position).id.hashCode(); }

    @NonNull @Override public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CategoryViewHolder holder = new CategoryViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_row, parent, false));
        holder.apps.setRecycledViewPool(sharedPool);
        return holder;
    }

    @Override public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.rowId = row.id;
        holder.title.setText(row.title);
        holder.count.setText(String.valueOf(row.apps.size()));
        AppRowAdapter adapter = adapters.get(row.id);
        if (adapter == null) {
            adapter = new AppRowAdapter(preferences, listener, row.id, this::onAppFocused);
            adapters.put(row.id, adapter);
        }
        if (holder.apps.getAdapter() != adapter) holder.apps.setAdapter(adapter);
        adapter.setApps(row.apps);
        Parcelable state = layoutStates.get(row.id);
        if (state != null) holder.layoutManager.onRestoreInstanceState(state);
    }

    @Override public void onViewRecycled(@NonNull CategoryViewHolder holder) {
        if (holder.rowId != null) layoutStates.put(holder.rowId, holder.layoutManager.onSaveInstanceState());
        super.onViewRecycled(holder);
    }

    @Override public int getItemCount() { return rows.size(); }

    static final class CategoryViewHolder extends RecyclerView.ViewHolder {
        final TextView title, count;
        final RecyclerView apps;
        final LinearLayoutManager layoutManager;
        String rowId;

        CategoryViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.txtCategoryTitle);
            count = view.findViewById(R.id.txtItemCount);
            apps = view.findViewById(R.id.recyclerApps);
            layoutManager = new LinearLayoutManager(view.getContext(), LinearLayoutManager.HORIZONTAL, false);
            apps.setLayoutManager(layoutManager);
            apps.setHasFixedSize(true);
            apps.setClipChildren(false);
            apps.setClipToPadding(false);
        }
    }
}
