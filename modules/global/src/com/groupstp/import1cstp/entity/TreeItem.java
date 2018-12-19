package com.groupstp.import1cstp.entity;

import javax.persistence.Entity;
import javax.persistence.Table;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.entity.StandardEntity;

import java.util.ArrayList;
import java.util.List;

@NamePattern("%s|name")
@MetaClass(name = "import1cstp$TreeItem")
public class TreeItem extends BaseUuidEntity {
    private static final long serialVersionUID = 5679128075640666523L;

    @MetaProperty
    protected TreeItem parent;

    @MetaProperty
    protected String stringValue1;

    @MetaProperty
    protected String stringValue2;

    @MetaProperty
    protected String stringValue3;

    @MetaProperty
    protected String name;

    @MetaProperty
    protected List<TreeItem> children;

    @MetaProperty
    protected StandardEntity entity;


    public void setStringValue1(String stringValue1) {
        this.stringValue1 = stringValue1;
    }

    public String getStringValue1() {
        return stringValue1;
    }


    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


    public void addChild(TreeItem child){
        if(children==null) children=new ArrayList<>();
        children.add(child);
    }

    public void setParent(TreeItem parent) {
        this.parent = parent;
    }

    public TreeItem getParent() {
        return parent;
    }

    public void setChildren(List<TreeItem> children) {
        this.children = children;
    }

    public List<TreeItem> getChildren() {
        return children;
    }

    public void setEntity(StandardEntity entity) {
        this.entity = entity;
    }

    public StandardEntity getEntity() {
        return entity;
    }


    public String getStringValue2() {
        return stringValue2;
    }

    public void setStringValue2(String stringValue2) {
        this.stringValue2 = stringValue2;
    }

    public String getStringValue3() {
        return stringValue3;
    }

    public void setStringValue3(String stringValue3) {
        this.stringValue3 = stringValue3;
    }
}