package com.groupstp.import1cstp.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.app.SchedulingService;
import com.haulmont.cuba.core.entity.ScheduledTask;
import com.haulmont.cuba.core.entity.ScheduledTaskDefinedBy;
import com.haulmont.cuba.core.entity.SchedulingType;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authenticated;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.persistence.Column;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service(ImportControllerService.NAME)
public class ImportControllerServiceBean implements ImportControllerService {

    @Inject
    private Sync1CService sync1CService;

    @Inject
    private EntityImportService entityImportService;

    @Inject
    private SettingsService settingsService;

    @Inject
    private QueryDaoService queryDaoService;

    @Inject
    private Persistence persistence;

    @Inject
    private Metadata metadata;

    @Inject
    private DataManager dataManager;

    @Override
    public String directImportFrom1C(String query) {
        String url="";
        String pass="";
        String username="";
        String result=null;
        try {
            url = (String) settingsService.getObjectValue(urlSettingsKey);
            pass = (String) settingsService.getObjectValue(passwordSettingsKey);
            username=(String) settingsService.getObjectValue(userNameSettingsKey);
            result = sync1CService.getStringData1C(url,pass,username,query);
        } catch (Exception e) {
            return result;
        }
        return result;
    }

    private Map<String, Float> progressMap = new ConcurrentHashMap<>();


    @Override
    public List<String> getAllDirectories(String url, String pass) {
        HashMap<String, String> params = new HashMap<>();
        List<String> result = new ArrayList<>();
        params.put("references", "");

        JsonArray data = null;
        try {
            data = (JsonArray) sync1CService.getData1C(url + "references", pass, params);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if (data == null) return result;
        for (JsonElement e : data) {
            JsonObject o = e.getAsJsonObject();
            String value = o.get("Имя").getAsString();
            result.add(value);
        }
        return result;

    }

    @Override
    public List<String> getDirectoryFields(String url, String pass, String reference) {
        HashMap<String, String> params = new HashMap<>();
        List<String> result = new ArrayList<>();
        params.put("reference", reference);
        params.put("type", "attributes");
        JsonArray data = null;
        try {
            data = (JsonArray) sync1CService.getData1C(url + "references", pass, params);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if (data == null) throw new RuntimeException("no attributes for " + reference);
        for (JsonElement e : data) {
            JsonObject o = e.getAsJsonObject();
            String value = o.get("Реквизит").getAsString();
            result.add(value);
        }
        return result;
    }

    @Override
    @Authenticated
    public void importFrom1C(String url, String pass, String directoryName) {

        String fieldAssociations;
        try {
            fieldAssociations = (String) settingsService.getObjectValue(directoryKey + "-" + directoryName);
        } catch (NullPointerException e) {
            return;
        }
        if ("".equals(fieldAssociations)) return;
        progressMap.put(directoryName, (float) 0);
        String entityName = fieldAssociations.substring(0, fieldAssociations.indexOf("."));

        List<String> associationList = Arrays.asList(fieldAssociations.split(";"))
                .stream()
                .map(item -> item.replace(entityName + ".", ""))
                .collect(Collectors.toList());
        Map<String, String> associationMap = associationList
                .stream()
                .collect(Collectors.toMap(
                        item -> item.substring(0, item.indexOf("-"))
                        , item -> item.substring(item.indexOf("-") + 1)
                ));
        List<String> attributesToLoad = associationList
                .stream()
                .map(item -> item.substring(item.indexOf("-") + 1))
                .collect(Collectors.toList());
        ;
        EntityJSONAdapter adapter = new EntityJSONAdapter();

        boolean associationsContainExtId = false;
        for (Map.Entry<String, String> entry : associationMap.entrySet()) {
            if ("extId".equals(entry.getKey())) {
                associationsContainExtId = true;
                break;
            }
        }
        if (!associationsContainExtId) {
            attributesToLoad.add("УникальныйИдентификатор");
            adapter.addFieldDescription("УникальныйИдентификатор", "extId", 50);
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("reference", directoryName);
        params.put("type", "data");
        params.put("attributes", "[" + attributesToLoad.stream().map(item -> "\"" + item + "\"").collect(Collectors.joining(",")) + "]");

        //загружаем только те поля, которые есть в справочнике настроек
        JsonArray data = null;
        try {
            data = (JsonArray) sync1CService.getData1C(url + "references", pass, params);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if ((data == null) || (data.size() == 0)) return;

        //если в ответе есть поле в формате имяПоляУникальныйИдентификатор, то именно оно будет соответствовать полю сущности
        for (Map.Entry<String, String> entry : associationMap.entrySet()) {
            if (data.get(0).getAsJsonObject().get(entry.getValue() + "УникальныйИдентификатор") != null) {
                putValueToAdapter(adapter, entry.getValue() + "УникальныйИдентификатор", entry.getKey(), entityName);
            } else {
                putValueToAdapter(adapter, entry.getValue(), entry.getKey(), entityName);
            }
        }

        boolean creationDenied = false;
        try {
            creationDenied = (boolean) settingsService.getObjectValue(directoryKey + "-" + directoryName + ".denyCreation");
        } catch (Exception e) {

        }

        int i = 0;
        List<JsonElement> errors = new ArrayList<>();
        for (JsonElement e : data) {

            if (i == 0) {  //первый объект содержит имена колонок
                i++;
                continue;
            }

            try {
                entityImportService.importData(adapter.prepareJSONForImport(e.getAsJsonObject(), entityName).getAsJsonObject(), creationDenied);
                progressMap.put(directoryName, ((float) i / (float) data.size()));

            } catch (Exception e1) {
                e1.printStackTrace();
                errors.add(e);
            }
            i++;
            // if(i==20) return;
        }
        entityImportService.clearCache();

    }

    @Override
    public Boolean isSyncScheduledTaskActiveForDirectory(String directoryName) {
        ScheduledTask task = queryDaoService.getSyncScheduledTaskForDirectory(directoryName);
        if (task == null) return false;
        return task.getActive();
    }

    @Override
    public void updateSyncForDirectory(String directoryName, Boolean syncActive, int period, Date beginDate) {
        String url, pass;
        try {
            url = (String) settingsService.getObjectValue(urlSettingsKey);
            pass = (String) settingsService.getObjectValue(passwordSettingsKey);
        } catch (Exception e) {
            return;
        }
        ScheduledTask task = queryDaoService.getSyncScheduledTaskForDirectory(directoryName);
        if (task == null) {
            task = metadata.create(ScheduledTask.class);
            task.setDefinedBy(ScheduledTaskDefinedBy.BEAN);
            task.setSchedulingType(SchedulingType.PERIOD);
            task.setBeanName("import1cstp_ImportControllerService");
            task.setMethodName("importFrom1C");

        }
        task.setPeriod(period * 3600); //из часов в секунды
        task.setStartDate(beginDate);
        task.setMethodParamsXml(makeMethodParamXml(url, pass, directoryName));

        //schedulingService.setActive(task,syncActive);
        task.setActive(syncActive);

        dataManager.commit(task);

    }

    private String makeMethodParamXml(String url, String password, String directoryName) {
        return "<?xml version='1.0' encoding='UTF-8'?>" +
                " <params>" +
                "<param type='java.lang.String' name='url'>" + url + "</param>" +
                "<param type='java.lang.String' name='pass'>" + password + "</param>" +
                "<param type='java.lang.String' name='directoryName'>" + directoryName + "</param>" +
                "</params>";
    }

    @Override
    public Float getProgress(String directoryName) {
        Float result = progressMap.get(directoryName);
        if (result == null) return (float) 0;
        else return result;
    }


    private void putValueToAdapter(EntityJSONAdapter adapter, String directoryField, String entityField, String entityName) {
        int length;
        try {
            length = metadata.getSession().getClassNN(entityName).getJavaClass().getDeclaredField(entityField).getAnnotation(Column.class).length();
        } catch (NoSuchFieldException e) {
            return;
        } catch (Exception e) {
            length = 50;
        }
        adapter.addFieldDescription(directoryField, entityField, length);
    }


    class EntityJSONAdapter {

        private Map<String, String> fieldNameMap = new HashMap<>();
        private Map<String, Integer> fieldLengthMap = new HashMap<>();

        JsonObject prepareJSONForImport(JsonObject obj, String type) {

            JsonObject result = new JsonObject();

            result.addProperty("type", type);

            obj.entrySet().forEach(entry -> {

                if ((fieldNameMap.get(entry.getKey()) != null) && (!(entry.getValue() instanceof JsonNull))) {
                    result.addProperty(fieldNameMap.get(entry.getKey()),
                            fieldLengthMap.get(entry.getKey()) == null ?
                                    entry.getValue().getAsString() :
                                    entry.getValue().getAsString().length() >= fieldLengthMap.get(entry.getKey()) ?
                                            entry.getValue().getAsString().substring(0, fieldLengthMap.get(entry.getKey()) - 1)
                                            : entry.getValue().getAsString()
                    );
                }

            });

            return result;

        }

        public void addFieldDescription(String jsonName, String javaName, int length) {
            fieldNameMap.put(jsonName, javaName);
            fieldLengthMap.put(jsonName, length);
        }

        public void addFieldDescription(String jsonName, String javaName) {
            fieldNameMap.put(jsonName, javaName);

        }

        public void clearMaps() {
            fieldLengthMap.clear();
            fieldNameMap.clear();
        }

    }
}

