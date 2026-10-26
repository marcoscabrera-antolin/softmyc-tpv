package com.softmyc.tpv.presentation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.Category;
import java.util.ArrayList;
import java.util.List;

public class CategoriaAdapter extends RecyclerView.Adapter<CategoriaAdapter.ViewHolder> {

    public interface OnCategorySelectedListener {
        void onCategorySelected(Category category);
    }

    private List<Category> categories = new ArrayList<>();
    private final OnCategorySelectedListener listener;

    public CategoriaAdapter(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_categoria_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(categories.get(position), position);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final Chip chipCategory;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            chipCategory = itemView.findViewById(R.id.chipCategoria);
        }

        public void bind(Category category, int position) {
            chipCategory.setText(category.getName());
            chipCategory.setChecked(category.isSelected());

            chipCategory.setOnClickListener(v -> {
                for (Category c : categories) c.setSelected(false);
                category.setSelected(true);
                notifyDataSetChanged();
                listener.onCategorySelected(category);
            });
        }
    }
}
