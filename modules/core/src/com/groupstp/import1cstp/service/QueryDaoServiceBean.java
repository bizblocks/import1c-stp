package com.groupstp.import1cstp.service;

import com.groupstp.import1cstp.entity.Settings;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.entity.ScheduledTask;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.security.entity.*;
import org.eclipse.persistence.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.*;

@Service(QueryDaoService.NAME)
public class QueryDaoServiceBean implements QueryDaoService {

    @Inject
    private DataManager dataManager;

    @Inject
    private Metadata metadata;

    @Inject
    private com.haulmont.cuba.core.Persistence persistence;

    @Override
    public StandardEntity getEntity(String entityType, String entityUUID) {
        if((entityType==null)||(entityUUID==null))return null;

        Transaction tx = persistence.createTransaction();
        Object en;
        try {
            EntityManager em = persistence.getEntityManager();
            en = em.createQuery(
                    "SELECT e FROM " + getMetaclassPrefix(entityType) + entityType + " e where e.id=:id")
                    .setParameter("id", UUID.fromString(entityUUID))
                    .getSingleResult();


            tx.commit();
        }
        finally {
            tx.end();
        }

        return (StandardEntity)en;
    }

    @Override
    public String getMetaclassPrefix(String entityType) {
        Collection<MetaClass> allPersistentMetaClasses = metadata.getTools().getAllPersistentMetaClasses();
        for (MetaClass metaClass : allPersistentMetaClasses) {
            String[] split = metaClass.getJavaClass().getSimpleName().split("$");
            if (split[0].equals("import1cstp")) {
                continue;
            }
            if (split[1].equals(entityType)) {
                return split[0];
            }
        }
        return null;
    }

    @Override
    public ScheduledTask getSyncScheduledTaskForDirectory(String directoryName) {
        LoadContext<ScheduledTask> loadContext = LoadContext.create(ScheduledTask.class)
                .setQuery(LoadContext.createQuery("select st from sys$ScheduledTask st where\n" +
                        "st.beanName=:beanNameItem and st.methodParamsXml like :methodParamsItem")
                        .setParameter("beanNameItem","import1cstp_ImportControllerService")
                        .setParameter("methodParamsItem","%"+directoryName+"%")

                );


        return dataManager.load(loadContext);
    }

    @Override
    public ScheduledTask getAnySyncScheduledTask() {
        LoadContext<ScheduledTask> loadContext = LoadContext.create(ScheduledTask.class)
                .setQuery(LoadContext.createQuery("select st from sys$ScheduledTask st where\n" +
                        "st.beanName=:beanNameItem ")
                        .setParameter("beanNameItem","import1cstp_ImportControllerService")
                );
        List<ScheduledTask> result=dataManager.loadList(loadContext);
        if((result==null)||(result.size()==0)) return null;
        return result.get(0);
    }

    /**
     * Достает объект настройки по ключу
     * @Author AndreyKolosov
     * @param key ключ
     * @return возвращает объект настройки
     */
    @Override
    public Settings getSettings(String key) {
        if((key==null))return null;

        LoadContext<Settings> loadContext = LoadContext.create(Settings.class)
                .setQuery(LoadContext.createQuery("SELECT h FROM import1cstp$Settings h where h.key=:key")
                        .setParameter("key", key));

        return dataManager.load(loadContext);
    }

}