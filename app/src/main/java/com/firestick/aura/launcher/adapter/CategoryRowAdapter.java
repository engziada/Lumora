package com.firestick.aura.launcher.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.model.AppCategory;

import java.util.ArrayList;
import java.util.List;

public class CategoryRowAdapter extends RecyclerView.Adapter<CategoryRowAdapter.CategoryViewHolder> {
    private final List<AppCategory> categories = new ArrayList<>();
    private final AppRowAdapter.OnAppActionListener listener;
    private final PreferenceManager preferences;

    public CategoryRowAdapter(PreferenceManager preferences, AppRowAdapter.OnAppActionListener listener) {
        this.preferences = preferences;
        this.listener = listener;
    }

    public void setCategories(List<AppCategory> values) {
        categories.clear();
        if (values != null) categories.addAll(values);
        notifyDataSetChanged();
    }

    public List<AppCategory> getCategories() { return categories; }

    @NonNull @Override public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CategoryViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_row, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(categories.get(position), preferences, listener);
    }

    @Override public int getItemCount() { return categories.size(); }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView title, count;
        private final RecyclerView apps;

        CategoryViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.txtCategoryTitle);
            count = view.findViewById(R.id.txtItemCount);
            apps = view.findViewById(R.id.recyclerApps);
            apps.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.HORIZONTAL, false));
            apps.setHasFixedSize(true);
            apps.setClipChildren(false);
            apps.setClipToPadding(false);
        }

        void bind(AppCategory category, PreferenceManager preferences, AppRowAdapter.OnAppActionListener listener) {
            title.setText(category.getTitle());
            count.setText(String.valueOf(category.getApps().size()));
            AppRowAdapter adapter = new AppRowAdapter(preferences, listener, category.getId());
            apps.setAdapter(adapter);
            adapter.setApps(category.getApps());
        }
    }
}
