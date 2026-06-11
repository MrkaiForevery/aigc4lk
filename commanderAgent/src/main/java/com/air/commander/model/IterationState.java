package com.air.commander.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 循环校验执行模式-迭代状态实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IterationState implements Serializable {
    private int iteration;          // 当前迭代次数
    private String phase;           // MAIN / EVALUATE / CORRECT
    private int mainStepIndex;      // 主步骤序列中下一个要执行的步骤索引
}