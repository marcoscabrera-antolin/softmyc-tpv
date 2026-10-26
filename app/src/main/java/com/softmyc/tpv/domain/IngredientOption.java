package com.softmyc.tpv.domain;

public class IngredientOption implements Comparable<IngredientOption> {
    public enum Type { QUITAR, ANADIR, SALSA }

    private String name;
    private double extraPrice;
    private Type type;
    private boolean isSelected;

    public IngredientOption(String name, double extraPrice, Type type) {
        this.name = name;
        this.extraPrice = extraPrice;
        this.type = type;
        this.isSelected = false;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getExtraPrice() { return extraPrice; }
    public void setExtraPrice(double extraPrice) { this.extraPrice = extraPrice; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    @Override
    public int compareTo(IngredientOption o) {
        if (this.name == null && (o == null || o.name == null)) return 0;
        if (this.name == null) return -1;
        if (o == null || o.name == null) return 1;
        return this.name.compareToIgnoreCase(o.name);
    }
}
