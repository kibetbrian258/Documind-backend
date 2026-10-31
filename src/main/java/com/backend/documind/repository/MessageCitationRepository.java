package com.backend.documind.repository;

import com.backend.documind.entity.MessageCitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

    List<MessageCitation> findAllByMessageIdInOrderByCitationNumberAsc(Collection<UUID> messageIds);
}