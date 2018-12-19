package com.groupstp.import1cstp.service;

import com.google.gson.JsonObject;
import com.haulmont.cuba.core.entity.Entity;
import java.io.Serializable;
import java.util.Collection;

public interface EntityImportService {
    String NAME = "import1cstp_EntityImportService";

    void clearCache();

    void addToCache(Collection<Entity> entities);

    Serializable createOrUpdateEntity(String data);

    Serializable importData(JsonObject e, Boolean creationDenied) throws Exception;

    Serializable importData(JsonObject e) throws Exception;

}