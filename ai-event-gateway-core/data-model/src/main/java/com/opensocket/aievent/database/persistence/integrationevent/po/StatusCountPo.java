package com.opensocket.aievent.database.persistence.integrationevent.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StatusCountPo {
    private String status;
    private int total;
}
