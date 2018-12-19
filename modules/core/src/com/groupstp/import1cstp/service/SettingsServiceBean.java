package com.groupstp.import1cstp.service;

import com.groupstp.import1cstp.entity.Settings;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.Metadata;
import org.springframework.stereotype.Service;

import javax.inject.Inject;


@Service(SettingsService.NAME)
public class SettingsServiceBean implements SettingsService {

    @Inject
    private Metadata metadata;

    @Inject
    private DataManager dataManager;

    @Inject
    private QueryDaoService queryDaoService;


    /**
     * Достает Значение у объекта settings
     *
     * @param settings
     * @return
     */
    @Override
    public Object getObjectValue(Settings settings) {

    if (settings.getText() != null) {
        return settings.getText();
    } else {
        return settings.getBooleanValue();
    }

    }

    /**
     * Достает Значение по ключу
     *
     * @param key Ключ
     * @return Объект-Значение
     */
    @Override
    public Object getObjectValue(String key) {
        Settings settings = queryDaoService.getSettings(key);
        return getObjectValue(settings);
    }

    /**
     * Создает/Изменяет настройку с ключем/значением
     *
     * @param key   Ключ
     * @param value Значение
     * @return Объект настройки
     */
    @Override
    public Settings setValue(String key, Object value) {
        if (key==null||value==null) {
            return null;
        }
        Settings settings = queryDaoService.getSettings(key);
        if (settings==null) {
            settings = metadata.create(Settings.class);
        } else {

            settings.setBooleanValue(null);
            settings.setText(null);
        }

        settings.setKey(key);
        Class<?> valueClass = value.getClass();

        if (valueClass.equals(String.class)) {
            settings.setText((String) value);
        }

        if (valueClass.equals(Boolean.class)) {
            settings.setBooleanValue((Boolean) value);
        }

        dataManager.commit(settings);
        return settings;
    }

}