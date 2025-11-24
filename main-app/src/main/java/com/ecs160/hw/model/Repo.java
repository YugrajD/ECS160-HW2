package com.ecs160.hw.model;

import com.ecs160.persistence.annotations.Id;
import com.ecs160.persistence.annotations.PersistableField;
import com.ecs160.persistence.annotations.PersistableObject;

@PersistableObject
public class Repo {
    @Id
    public String name;

    @PersistableField
    public String url;

    @PersistableField
    public String issues;


}
