package com.ecs160.hw.model;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;

@PersistableObject
public class Issue {
    @Id
    public String id;

    @PersistableField
    public String Description;
}
