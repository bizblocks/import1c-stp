package com.groupstp.import1cstp.service;

import java.util.Date;
import java.util.List;

public interface ImportControllerService {
    String NAME = "import1cstp_ImportControllerService";

    String directoryListSettingsKey="1CDirectories";
    String urlSettingsKey="1Curl";
    String passwordSettingsKey="1Cpassword";
    String userNameSettingsKey="1Cusername";
    String directoryKey="1CDirectory";

    List<String> getAllDirectories(String url, String pass);
    
    List<String> getDirectoryFields(String url, String pass, String reference);

    void importFrom1C(String url, String pass, String entityName);

    Boolean isSyncScheduledTaskActiveForDirectory(String entityName);

    void updateSyncForDirectory(String directoryName, Boolean syncActive, int period, Date beginDate);

    Float getProgress(String directoryName);
}