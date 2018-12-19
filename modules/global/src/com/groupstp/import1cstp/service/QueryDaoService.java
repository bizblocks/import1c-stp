package com.groupstp.import1cstp.service;

import com.groupstp.import1cstp.entity.Settings;
import com.haulmont.cuba.core.entity.ScheduledTask;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.security.entity.*;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;


public interface QueryDaoService {
    String NAME = "import1cstp_QueryDaoService";

    StandardEntity getEntity(String entityType, String entityUUID);

    String getMetaclassPrefix(String entityType);

    Settings getSettings(String key);

    ScheduledTask getSyncScheduledTaskForDirectory(String directoryName);

    ScheduledTask getAnySyncScheduledTask();

}