package com.groupstp.import1cstp.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.haulmont.chile.core.datatypes.impl.EnumClass;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.Session;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.Metadata;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service(EntityImportService.NAME)
public class EntityImportServiceBean implements EntityImportService {

    @Inject
    private Metadata metadata;

    @Inject
    private DataManager dataManager;

    @Inject
    private Logger log;

    @Inject
    private Persistence persistence;

    @Inject
    private QueryDaoService queryDaoService;

    private Map<String,Entity>  cache=new HashMap<>();
    private Map<String, Integer> useCount=new HashMap<>();

    @Override
    public void clearCache(){
        cache.clear();
        useCount.clear();
    }

    @Override
    public void addToCache(Collection<Entity> entities){
        entities.forEach(item->{
            try {
                if(item.getValue("extId")==null) return;
                cache.put(item.getValue("extId"), item);
                useCount.put(item.getValue("extId"),0);
            }
            catch (Exception e){
                return;
            }
        });
    }

    @Override
    public Serializable createOrUpdateEntity(String data) {
        clearCache();
        ArrayList<Serializable> res = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonElement element  = parser.parse(data);
        try {
            if(element.isJsonArray()) {
                for (JsonElement jsonElement : element.getAsJsonArray()) {
                    res.add(importData(jsonElement.getAsJsonObject()));
                }
                return res;
            }
            return importData(element.getAsJsonObject());
        }
        catch (Exception ex)
        {
            log.debug(ex.toString());
            return returnMessage(ex.toString());
        }
        finally {
            clearCache();
        }
    }

    @Override
    public Serializable importData(JsonObject e) throws Exception {
        return importData(e,false);
    }

    public Serializable importData(JsonObject e,Boolean creationDenied) throws Exception {
        try {
            if( e.get("id")!=null) {
                String name = e.get("type").getAsString();
                return queryDaoService.getEntity(name, e.get("id").getAsString());
            }
            if( e.get("extId")==null) return null;
            Entity entity = createObject(e, creationDenied);
            for (MetaProperty metaProperty : entity.getMetaClass().getProperties()) {
                setValue(entity, metaProperty, e);
            }
            String id = entity.getId().toString();
            Integer count = useCount.get(id) != null ? useCount.get(id) - 1 : 0;
            useCount.put(id, count);
            if (count == 0) {
                Entity result=dataManager.commit(entity);
                return result;
            }
            else
                return entity;
        }
        catch (CreationDeniedException ex){
            return null;
        }
    }

    private void setValue(Entity res, MetaProperty metaProperty, JsonObject e) throws Exception {
        if(!e.has(metaProperty.getName()))
            return;
        JsonElement val = e.get(metaProperty.getName());
        if(val.isJsonNull())
            return;
        if(val.isJsonPrimitive())
            val = e.getAsJsonPrimitive(metaProperty.getName());
        else if(val.isJsonArray())
            val = e.getAsJsonArray(metaProperty.getName());
        else
            val = e.getAsJsonObject(metaProperty.getName());

        if (val.isJsonArray()) {
            for (JsonElement jsonElement : val.getAsJsonArray()) {
                importData(jsonElement.getAsJsonObject());
            }
        }
        else if (metaProperty.getType().equals(MetaProperty.Type.ASSOCIATION) || metaProperty.getType().equals(MetaProperty.Type.COMPOSITION))
        {
            Entity impVal;

            if(e.get(metaProperty.getName()) instanceof JsonNull) return;

            //значит в json идэшник
            if(e.get(metaProperty.getName()).isJsonPrimitive()){

                if(e.get(metaProperty.getName()).getAsString().equalsIgnoreCase("00000000-0000-0000-0000-000000000000"))
                    impVal=null;
                else{
                    //String className = metaProperty.toString().substring(0,metaProperty.toString().indexOf("."));
                    String className=metaProperty.getRange().asClass().getName();
                    impVal = getEntity(className,
                            e.get(metaProperty.getName()).getAsString());
                    impVal.setValue("extId", e.get(metaProperty.getName()).getAsString());
                    if(checkMandatoryFields(impVal)) {
                        Transaction tx = persistence.createTransaction();
                        EntityManager em = persistence.getEntityManager();
                        em.persist(impVal);
                        tx.commit();
                    }
                }

            } else{
                impVal = (Entity) importData(e.getAsJsonObject(metaProperty.getName()));
            }
            res.setValue(metaProperty.getName(), impVal);
        }
        else if(metaProperty.getJavaType().equals(Integer.class))
            res.setValue(metaProperty.getName(), val.getAsInt());
        else if(metaProperty.getJavaType().equals(String.class))
            res.setValue(metaProperty.getName(), val.getAsString());
        else if(metaProperty.getJavaType().equals(Float.class))
            res.setValue(metaProperty.getName(), val.getAsFloat());
        else if(metaProperty.getJavaType().equals(Double.class))
            res.setValue(metaProperty.getName(), val.getAsDouble());
        else if(metaProperty.getJavaType().equals(Boolean.class))
            res.setValue(metaProperty.getName(), val.getAsBoolean());
        else if(metaProperty.getJavaType().equals(Date.class))
            res.setValue(metaProperty.getName(), parse(val.getAsString()));
        else if(metaProperty.getType().equals(MetaProperty.Type.ENUM)) {
            res.setValue(metaProperty.getName(), getEnumValue((EnumClass[]) metaProperty.getJavaType().getEnumConstants(), val.getAsString()));
        }
    }

    private boolean checkMandatoryFields(Entity entity){

        boolean changesMade=false;

        for (MetaProperty metaProperty : entity.getMetaClass().getProperties()) {
            if(metaProperty.isMandatory()){

                if(metaProperty.getName().equalsIgnoreCase("id")) break;
                if(entity.getValue(metaProperty.getName())!=null) break;

                if(metaProperty.getJavaType().equals(Integer.class)){
                    entity.setValue(metaProperty.getName(), 0);

                }
                else if(metaProperty.getJavaType().equals(String.class)){
                    // вдруг поле уникальное

                    entity.setValue(metaProperty.getName(),"temp ");
                }

                else if(metaProperty.getJavaType().equals(Float.class)){
                    entity.setValue(metaProperty.getName(), 0.);
                }

                else if(metaProperty.getJavaType().equals(Double.class)){
                    entity.setValue(metaProperty.getName(), 0.);
                }

                else if(metaProperty.getJavaType().equals(Boolean.class)){
                    entity.setValue(metaProperty.getName(),false);
                }

                else if(metaProperty.getJavaType().equals(Date.class)){
                    entity.setValue(metaProperty.getName(),new Date());
                }

                else if(metaProperty.getType().equals(MetaProperty.Type.ENUM)) {
                    entity.setValue(metaProperty.getName(), getEnumValue((EnumClass[]) metaProperty.getJavaType().getEnumConstants(),
                            ((EnumClass[]) metaProperty.getJavaType().getEnumConstants())[0].toString()));
                }
                changesMade=true;
            }

        }
        return changesMade;
    }

    private Object getEnumValue(EnumClass[] ev, String id)
    {
        for (EnumClass anEv : ev)
            if (anEv.toString().equals(id))
                return anEv;
        return null;
    }

    private Entity createObject(JsonObject e,boolean creationDenied) throws Exception {
        if(e.get("type")==null)
            throw new Exception("Type is NULL for "+e.getAsString());
        return getEntity(e.get("type").getAsString(), e.get("extId").getAsString(),creationDenied);
    }

    private Date parse(String d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        try {
            return f.parse(d);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new Date();
    }

    private Entity getEntity(String type,String id){
        try {
            return getEntity(type,id,false);
        } catch (CreationDeniedException e) {
            return null;  // такое исключение не будет брошено никогда, т.к передаем false
        }
    }

    private Entity getEntity(String type,String id,boolean creationDenied) throws CreationDeniedException {
        if(cache.get(id)!=null) {
            useCount.put(id, useCount.get(id)+1);
            return cache.get(id);
        }
        String viewName = (type.substring(type.indexOf("$")+1)).substring(0,1).toLowerCase()+(type.substring(type.indexOf("$")+1)).substring(1)+"-full";
        Entity entity=null;

        try {
            if (id!=null) {
                Session session = metadata.getSession();
                MetaClass metaClass = session.getClass(type);
                Class javaClass = metaClass.getJavaClass();
                entity=dataManager.load(javaClass)
                        .query("select e from "+type+" e where e.extId=:extId")
                        .parameter("extId",id)
                        .view(viewName)
                        .one();
            }
        }
        catch (Exception ex)
        {
            if(!ex.getMessage().equals("No results"))
                throw ex;
        }
        if(entity==null) {
            if(creationDenied) throw new CreationDeniedException();
            entity = metadata.create(type);
            entity.setValue("extId", id);    //т.к ид уже известен, надо будет его сетить и заполнять обязательные поля
        }

        useCount.put(id, 1);
        cache.put(id, entity);

        return entity;
    }

    private Serializable returnMessage(String message)
    {
        HashMap<String, String> res = new HashMap<>();
        res.put("ErrorMessage", message);
        return res;
    }

    class CreationDeniedException extends Exception{

    }

}