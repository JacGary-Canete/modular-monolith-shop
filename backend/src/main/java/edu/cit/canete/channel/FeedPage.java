package edu.cit.canete.channel;

import java.util.List;

public record FeedPage(List<FeedEvent> events, long nextCursor) {
}