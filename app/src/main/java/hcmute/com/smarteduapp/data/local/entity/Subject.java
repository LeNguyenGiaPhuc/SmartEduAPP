package hcmute.com.smarteduapp.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "subjects", indices = {@Index(value = {"name"}, unique = true)})
public class Subject {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name;

    public String description;
    public long createdAt;

    public Subject(@NonNull String name, String description, long createdAt) {
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }
}
