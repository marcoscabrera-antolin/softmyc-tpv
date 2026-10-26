package com.softmyc.tpv.domain;

public class Category {
    private String id;
    private String name;
    private int iconResId;
    private boolean isSelected;

    public Category(String id, String name, int iconResId, boolean isSelected) {
        this.id = id;
        this.name = name;
        this.iconResId = iconResId;
        this.isSelected = isSelected;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getIconResId() { return iconResId; }
    public void setIconResId(int iconResId) { this.iconResId = iconResId; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}
