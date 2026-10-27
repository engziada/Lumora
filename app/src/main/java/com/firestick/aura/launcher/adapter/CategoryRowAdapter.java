package com.firestick.aura.launcher.adapter;

import android.content.Context;
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
    private final PreferenceManager preferenceManager;

    public CategoryRowAdapter(PreferenceManager preferenceManager, AppRowAdapter.OnAppActionListener listener) {
        this.preferenceManager = preferenceManager;
        this.listener = listener;
    }

    public void setCategories(List<AppCategory> newCategories) {
        categories.clear();
        if (newCategories != null) {
            categories.addAll(newCategories);
        }
        notifyDataSetChanged();
    }

    public List<AppCategory> getCategories() {
        return categories;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_row, parent, false);
        return new CategoryViewHolder(view, preferenceManager);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        AppCategory category = categories.get(position);
        holder.bind(category, listener);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtTitle;
        private final TextView txtCount;
        private final View indicatorAccent;
        private final RecyclerView recyclerApps;
        private final PreferenceManager preferenceManager;
        private final Context context;

        public CategoryViewHolder(@NonNull View itemView, PreferenceManager preferenceManager) {
            super(itemView);
            this.preferenceManager = preferenceManager;
            this.context = itemView.getContext();
            this.txtTitle = itemView.findViewById(R.id.txtCategoryTitle);
            this.txtCount = itemView.findViewById(R.id.txtItemCount);
            this.indicatorAccent = itemView.findViewById(R.id.indicatorAccent);
            this.recyclerApps = itemView.findViewById(R.id.recyclerApps);

            LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
            recyclerApps.setLayoutManager(layoutManager);
            recyclerApps.setHasFixedSize(true);
            recyclerApps.setClipChildren(false);
            recyclerApps.setClipToPadding(false);
        }

        public void bind(AppCategory category, AppRowAdapter.OnAppActionListener listener) {
            txtTitle.setText(category.getTitle());
            txtCount.setText(String.valueOf(category.getApps().size()));

            AppRowAdapter rowAdapter = new AppRowAdapter(preferenceManager, listener);
            recyclerApps.setAdapter(rowAdapter);
            rowAdapter.setApps(category.getApps());
        }
    }
}
