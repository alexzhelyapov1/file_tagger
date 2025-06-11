package com.example.imagetagger.core.model;

import java.io.File;
import java.util.Objects;

// Временный POJO, пока мы не интегрировали полное сохранение в БД
// В будущем он будет содержать id, hash, tags и т.д.
public class TrackedFile {
    private final String absolutePath;
    private final File file; // Для удобства доступа к самому объекту File

    public TrackedFile(String absolutePath) {
        this.absolutePath = absolutePath;
        this.file = new File(absolutePath);
    }

    public TrackedFile(File file) {
        this.file = file;
        this.absolutePath = file.getAbsolutePath();
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return file.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackedFile that = (TrackedFile) o;
        return Objects.equals(absolutePath, that.absolutePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absolutePath);
    }

    @Override
    public String toString() {
        return "TrackedFile{" +
               "absolutePath='" + absolutePath + '\'' +
               '}';
    }
}