package com.az.accounts.service;

import com.az.accounts.domain.OutboxEvent;
import com.az.accounts.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxService(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, String aggregateId, String eventType, Object payload) {
        String json = jsonMapper.writeValueAsString(payload);
        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, json));
    }
}
