package com.example.imagetagger.core.model;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TrackedFile {
    private long id; // Идентификатор из БД
    private String absolutePath;
    private File file; // Для удобства доступа к самому объекту File
    private String contentHash;
    private long sizeBytes;
    private long modifiedDate; // Unix timestamp
    private long lastSeenDate; // Unix timestamp

    private Set<Tag> tags = new HashSet<>(); // Теги, присвоенные этому файлу

    // Конструктор для нового файла, еще не сохраненного в БД
    public TrackedFile(String absolutePath, String contentHash, long sizeBytes, long modifiedDate) {
        this.absolutePath = absolutePath;
        this.file = new File(absolutePath); // file создается здесь
        this.contentHash = contentHash;
        this.sizeBytes = sizeBytes;
        this.modifiedDate = modifiedDate;
        this.lastSeenDate = System.currentTimeMillis(); // Текущее время при создании/обнаружении
    }

    // Конструктор для файла, загруженного из БД
    public TrackedFile(long id, String absolutePath, String contentHash, long sizeBytes, long modifiedDate, long lastSeenDate) {
        this.id = id;
        this.absolutePath = absolutePath;
        this.file = new File(absolutePath); // file создается здесь
        this.contentHash = contentHash;
        this.sizeBytes = sizeBytes;
        this.modifiedDate = modifiedDate;
        this.lastSeenDate = lastSeenDate;
    }

    // Геттеры и Сеттеры
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
        this.file = new File(absolutePath); // Обновляем file при смене пути
    }

    public File getFile() {
        // Ленивая инициализация или обновление, если путь мог измениться
        if (this.file == null || !this.file.getAbsolutePath().equals(this.absolutePath)) {
            this.file = new File(this.absolutePath);
        }
        return file;
    }
    
    // Явно устанавливать File не будем, он должен быть производным от absolutePath

    public String getName() {
        return getFile().getName();
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public long getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(long modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public long getLastSeenDate() {
        return lastSeenDate;
    }

    public void setLastSeenDate(long lastSeenDate) {
        this.lastSeenDate = lastSeenDate;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags != null ? new HashSet<>(tags) : new HashSet<>();
    }

    public void addTag(Tag tag) {
        if (tag != null) {
            this.tags.add(tag);
        }
    }

    public void removeTag(Tag tag) {
        if (tag != null) {
            this.tags.remove(tag);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackedFile that = (TrackedFile) o;
        // Если оба объекта имеют ID (из БД), сравниваем по ID
        if (id > 0 && that.id > 0) {
            return id == that.id;
        }
        // Иначе (например, при сравнении нового объекта с существующим или двух новых)
        // сравниваем по абсолютному пути, так как он должен быть уникальным для файла на диске
        return Objects.equals(absolutePath, that.absolutePath);
    }

    @Override
    public int hashCode() {
        // Если ID есть, используем его для хэша
        if (id > 0) {
            return Objects.hash(id);
        }
        // Иначе используем абсолютный путь
        return Objects.hash(absolutePath);
    }

    @Override
    public String toString() {
        return "TrackedFile{" +
               "id=" + id +
               ", absolutePath='" + absolutePath + '\'' +
               // ", tags=" + tags.size() + // Раскомментировать для отладки, если нужно
               '}';
    }
}