package edu.cit.canete.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_cursor")
public class ChannelCursor {

    @Id
    private Integer id;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    protected ChannelCursor() {
    }

    public Integer getId() {
        return id;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public void setLastSeq(long lastSeq) {
        this.lastSeq = lastSeq;
    }
}