package com.playbit.entity;
import jakarta.persistence.*;
@Entity @Table(name="subjects")
public class Subject {
    @Id public String id;
    public String name;
    public String slug;
}
