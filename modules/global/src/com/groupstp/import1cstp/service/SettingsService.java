package com.groupstp.import1cstp.service;


import com.groupstp.import1cstp.entity.Settings;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public interface SettingsService {
    String NAME = "import1cstp_SettingsService";

    Object getObjectValue (Settings settings);

    Object getObjectValue(String key);

    Settings setValue(String key, Object object);

}