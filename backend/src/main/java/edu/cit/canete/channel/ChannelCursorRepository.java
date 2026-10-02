package edu.cit.canete.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChannelCursorRepository extends JpaRepository<ChannelCursor, Integer> {
}