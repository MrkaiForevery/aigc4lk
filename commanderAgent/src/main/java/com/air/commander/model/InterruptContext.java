package com.air.commander.model;

import io.seata.tm.api.transaction.SuspendedResourcesHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterruptContext implements Serializable {
    private String xid;
    private String userId;
    private String stepId;
    private String commandType;
    private List<String> requiredScopes;
    private SuspendedResourcesHolder transactionHolder;  // 事务快照
}