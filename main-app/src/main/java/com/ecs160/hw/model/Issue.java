package com.ecs160.hw.model;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;

public class Issue {
    @Id
    public String issueID;

    @PersistableField
    public String description;
}
