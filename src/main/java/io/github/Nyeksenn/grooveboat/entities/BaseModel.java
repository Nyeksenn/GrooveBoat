package io.github.Nyeksenn.grooveboat.entities;

import io.ebean.Model;
import io.ebean.annotation.WhenCreated;
import io.ebean.annotation.WhenModified;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.time.Instant;

@MappedSuperclass
public class BaseModel extends Model {
    @Id
    long id;

    @Version
    Long version;

    @WhenCreated
    Instant whenCreated;

    @WhenModified
    Instant whenModified;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getWhenCreated() {
        return whenCreated;
    }

    public void setWhenCreated(Instant whenCreated) {
        this.whenCreated = whenCreated;
    }

    public Instant getWhenModified() {
        return whenModified;
    }

    public void setWhenModified(Instant whenModified) {
        this.whenModified = whenModified;
    }
}
