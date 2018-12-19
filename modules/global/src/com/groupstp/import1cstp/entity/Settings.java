package com.groupstp.import1cstp.entity;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;

import java.math.BigDecimal;
import java.util.Date;

@NamePattern("%s|key")
@Table(name = "IMPORT1CSTP_SETTINGS")
@Entity(name = "import1cstp$Settings")
public class Settings extends StandardEntity {
    private static final long serialVersionUID = -3998204428933498195L;

    @NotNull
    @Column(name = "KEY_", nullable = false, unique = true, length = 50)
    protected String key;

    @Column(name = "TEXT", length = 1000)
    protected String text;

    @Column(name = "BOOLEAN_VALUE")
    protected Boolean booleanValue;

    public void setBooleanValue(Boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }


}