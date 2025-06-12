package com.example.imagetagger.core.model;

import java.util.Objects;

public class Tag {
    private long id; // Идентификатор из БД
    private String name;

    // Конструктор для создания нового тега (id еще не присвоен)
    public Tag(String name) {
        this.name = name;
    }

    // Конструктор для тега, загруженного из БД (с id)
    public Tag(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        // Теги считаются одинаковыми, если у них одинаковое имя (игнорируя регистр)
        // или если у них одинаковый id (если он установлен, т.е. > 0)
        if (id > 0 && tag.id > 0) {
            return id == tag.id;
        }
        return name.equalsIgnoreCase(tag.name);
    }

    @Override
    public int hashCode() {
        // Используем имя в нижнем регистре для хэш-кода, чтобы соответствовать equals
        return Objects.hash(name.toLowerCase());
    }

    @Override
    public String toString() {
        // Это будет отображаться в ListView, поэтому только имя
        return name;
    }
}