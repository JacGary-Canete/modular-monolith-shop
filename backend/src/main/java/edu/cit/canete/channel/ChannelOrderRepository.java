package edu.cit.canete.channel;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChannelOrderRepository extends JpaRepository<ChannelOrder, Long> {
    Optional<ChannelOrder> findByTiangeOrderId(String tiangeOrderId);
    java.util.List<ChannelOrder> findByDecisionAndResolved(String decision, boolean resolved);
}