package com.groupstp.import1cstp.web;

import com.groupstp.import1cstp.entity.TreeItem;
import com.groupstp.import1cstp.service.ImportControllerService;
import com.groupstp.import1cstp.service.QueryDaoService;
import com.groupstp.import1cstp.service.SettingsService;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.entity.ScheduledTask;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.core.sys.SecurityContext;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.actions.BaseAction;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.executors.BackgroundTask;
import com.haulmont.cuba.gui.executors.BackgroundTaskHandler;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.TaskLifeCycle;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Importsettings extends AbstractWindow {

    @Inject
    private CollectionDatasource<TreeItem,UUID> directoryDs;

    @Inject
    private CollectionDatasource<TreeItem,UUID> selectedDirectoryDs;

    @Inject
    private CollectionDatasource<TreeItem,UUID> systemEntityDs;

    @Inject
    private CollectionDatasource<TreeItem,UUID> associationDs;

    @Inject
    private CollectionDatasource<TreeItem,UUID> directoryFieldsDs;

    @Inject
    private CollectionDatasource<TreeItem,UUID> entityFieldsDs;

    @Inject
    private CollectionDatasource<TreeItem,UUID> configuredDirectoryDs;

    @Inject
    private Table<TreeItem> syncTaskTable;

    @Inject
    private TextField urlField;

    @Inject
    private TextField passwordField;

    @Inject
    private Accordion accordion;

    @Inject
    private Label checkConnectionLabel;

    @Inject
    private TwinColumn twinColumn;

    @Inject
    private TextField periodField;

    @Inject
    private DateField beginDateField;

    @Inject
    private CheckBox denyCreationCheckbox;

    @Inject
    private Button addAssociationBtn;

    @Inject
    private Button deleteAssociationBtn;

    @Inject
    private LookupField entityLookupField;

    @Inject
    private ImportControllerService importControllerService;

    @Inject
    private SettingsService settingsService;

    @Inject
    private QueryDaoService queryDaoService;

    @Inject
    private Metadata metadata;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private ComponentsFactory componentsFactory;

    private Map<String,Object> dirtyData=new HashMap<>();
    private String directoryListSettingsKey=ImportControllerService.directoryListSettingsKey;
    private String urlSettingsKey=ImportControllerService.urlSettingsKey;
    private String passwordSettingsKey=ImportControllerService.passwordSettingsKey;
    private String directoryKey=ImportControllerService.directoryKey;

    private boolean ignoreEntityChange=false;

    private Map<String,Component> syncNowComponentMap=new HashMap<>();

    @Override
    public void init(Map<String, Object> params) {
        try{
            urlField.setValue(settingsService.getObjectValue(urlSettingsKey));
        }
        catch(Exception e){}
        try{
            passwordField.setValue(settingsService.getObjectValue(passwordSettingsKey));
        }
        catch(Exception e){}
        addAssociationBtn.setEnabled(false);

        ScheduledTask syncTask=queryDaoService.getAnySyncScheduledTask();

        if(syncTask!=null){
            periodField.setValue(syncTask.getPeriod()/3600);
            beginDateField.setValue(syncTask.getStartDate());
        }

        initDataSources();
        refreshStaticDs();
        initComponents();


    }

    private void initComponents(){

        denyCreationCheckbox.setEnabled(false);
        denyCreationCheckbox.addValueChangeListener(value->{
            try {
                settingsService.setValue(directoryKey + "-" + selectedDirectoryDs.getItem().getName() + ".denyCreation", value.getValue());
            }
            catch (Exception e){}
        });

        entityLookupField.addValueChangeListener(value->{
            if (value.getValue() != null) refreshEntityFieldsDs(((TreeItem) value.getValue()).getName());
            else entityFieldsDs.clear();
            refreshAssociationDs();
        });

        accordion.addSelectedTabChangeListener(value->{
            refreshStaticDs();
            refreshSelectedDirectoryDs();
            refreshConfiguredDirectoryDs();
            try{
                Collection twinColumnValue=Arrays.asList(((String)settingsService.getObjectValue(directoryListSettingsKey)).split(";"))
                        .stream().map(item->getTreeItemByName(directoryDs.getItems(),item)).collect(Collectors.toList());
                twinColumn.setValue(twinColumnValue);
            }
            catch (Exception e){

            }
        });

        try{
            Collection twinColumnValue=Arrays.asList(((String)settingsService.getObjectValue(directoryListSettingsKey)).split(";"))
                    .stream().map(item->getTreeItemByName(directoryDs.getItems(),item)).collect(Collectors.toList());
            twinColumn.setValue(twinColumnValue);
        }
        catch (Exception e){

        }
        twinColumn.addValueChangeListener(value->{
            if(value.getValue()!=null)updateUsingDirectories(((Collection<TreeItem>)value.getValue()).stream().map(item->item.getName()).collect(Collectors.toList()));
            else updateUsingDirectories(Arrays.asList(""));
        });

        syncTaskTable.addGeneratedColumn(getMessage("use_in_sync"),item->{
            CheckBox checkBox=componentsFactory.createComponent(CheckBox.class);
            checkBox.setValue(importControllerService.isSyncScheduledTaskActiveForDirectory(item.getStringValue2()));
            checkBox.addValueChangeListener(value->{
                if((periodField.getValue()==null)||(beginDateField.getValue()==null)){
                    if((boolean)value.getValue()) {
                        showNotification(getMessage("error"), getMessage("fill_period_and_begin_date"), NotificationType.WARNING);
                        checkBox.setValue(false);
                    }
                }
                else importControllerService.updateSyncForDirectory(item.getStringValue2(),(Boolean)value.getValue(),periodField.getValue(),beginDateField.getValue());
            });
            return checkBox;
        });
        syncTaskTable.addGeneratedColumn(getMessage(" "),item->{
            if(syncNowComponentMap.get(item.getStringValue1())!=null) return syncNowComponentMap.get(item.getStringValue1());  //чтобы при смене вкладок не создавать компоненты заново, там м.б рабочий прогресс бар

            HBoxLayout layout=componentsFactory.createComponent(HBoxLayout.class);
            syncNowComponentMap.put(item.getStringValue1(),layout);
            layout.setWidth("100%");

            Button button=componentsFactory.createComponent(Button.class);
            button.setCaption(getMessage("sync_now"));
            layout.add(button);

            ProgressBar progressBar=componentsFactory.createComponent(ProgressBar.class);
            progressBar.setVisible(false);
            progressBar.setWidth("100%");
            layout.add(progressBar);

            final SecurityContext securityContext = AppContext.getSecurityContext();

            button.setAction(new BaseAction("sync_now"){
                @Override
                public void actionPerform(Component component) {

                    BackgroundTask<Integer, Void> task = new BackgroundTask<Integer, Void>(3600, TimeUnit.SECONDS, getFrame()) {
                        Thread thread= new Thread(new Runnable() {
                            @Override
                            public void run() {
                                AppContext.setSecurityContext(securityContext);
                                importControllerService.importFrom1C(urlField.getRawValue(),passwordField.getRawValue(),item.getStringValue1());
                            }
                        });

                        @Override
                        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {

                            thread.start();
                            while (thread.isAlive()){
                                TimeUnit.SECONDS.sleep(1);
                                taskLifeCycle.publish(0);
                            }

                            return null;
                        }

                        @Override
                        public void progress(List<Integer> changes) {
                            progressBar.setValue(importControllerService.getProgress(item.getStringValue1()));
                        }
                        @Override
                        public void done(Void result) {
                            progressBar.setVisible(false);
                            progressBar.setValue((float)0);
                            button.setVisible(true);
                            showNotification(item.getStringValue1(),getMessage("import_completed"),NotificationType.TRAY);
                        }
                    };

                    button.setVisible(false);
                    progressBar.setVisible(true);
                    BackgroundTaskHandler taskHandler = backgroundWorker.handle(task);
                    taskHandler.execute();
                }
            });

            return layout;
        });
    }


    private void initDataSources(){
        selectedDirectoryDs.addItemChangeListener(item->{
            TreeItem entityForSelectedDirectory=getEntityForSelectedDirectory();
            ignoreEntityChange=true;

            if(item.getItem()!=null) refreshDirectoryFieldsDs(item.getItem().getName());
            if(entityForSelectedDirectory!=null)refreshEntityFieldsDs(entityForSelectedDirectory.getName());
            updateDenyCheckboxValueAndState();
            entityLookupField.setValue(entityForSelectedDirectory);
           // refreshAssociationDs();

        });

        systemEntityDs.addItemChangeListener(item->{
            if(ignoreEntityChange) ignoreEntityChange=false;
            else {
                if (associationDs.getItems().size() != 0) {
                    showOptionDialog(
                            getMessage("changing_entity"),
                            getMessage("on_change_entity_msg"),
                            MessageType.CONFIRMATION,
                            new Action[]{
                                    new DialogAction(DialogAction.Type.OK) {
                                        @Override
                                        public void actionPerform(Component component) {
                                            deleteAssociations(associationDs.getItems());
                                            refreshDirectoryFieldsDs(selectedDirectoryDs.getItem().getName());
                                            refreshEntityFieldsDs(item.getItem().getName());
                                        }
                                    },
                                    new DialogAction(DialogAction.Type.CANCEL){
                                        @Override
                                        public void actionPerform(Component component) {
                                            ignoreEntityChange=true;
                                            entityLookupField.setValue(item.getPrevItem());

                                        }
                                    }
                            }
                    );
                }
            }
        });

        directoryFieldsDs.addItemChangeListener(item->checkAddAssociationBtnEnabled());
        entityFieldsDs.addItemChangeListener(item->checkAddAssociationBtnEnabled());
    }

    private void updateDenyCheckboxValueAndState() {
        denyCreationCheckbox.setEnabled(selectedDirectoryDs.getItem()!=null);

        try {
            denyCreationCheckbox.setValue(settingsService.getObjectValue(directoryKey + "-" + selectedDirectoryDs.getItem().getName()+".denyCreation"));
        }
        catch (Exception e){
            denyCreationCheckbox.setValue(false);
        }
    }

    private void updateUsingDirectories(List<String> directories){
        String value=directories.stream().collect(Collectors.joining(";"));
        if(!"".equals(value)) value=value+";";
        settingsService.setValue(directoryListSettingsKey,value);
    }

    private void deleteAssociations(Collection<TreeItem> items) {
        clearAssociations(selectedDirectoryDs.getItem().getName());
        associationDs.clear();
    }

    private void checkAddAssociationBtnEnabled(){
        if((directoryFieldsDs.getItem()!=null)&&(entityFieldsDs.getItem()!=null)) addAssociationBtn.setEnabled(true);
        else addAssociationBtn.setEnabled(false);
    }

    private void refreshConfiguredDirectoryDs() {
        configuredDirectoryDs.clear();

        selectedDirectoryDs.getItems().forEach(item->{
            try{
                settingsService.getObjectValue(directoryKey+"-"+item.getName());
                TreeItem treeItem=metadata.create(TreeItem.class);
                treeItem.setStringValue1(item.getName());
                treeItem.setStringValue2(getEntityForDirectory(directoryKey+"-"+item.getName()));
                configuredDirectoryDs.addItem(treeItem);
            }
            catch (Exception e){
                int i=0;i++;
            }
        });

    }

    private void refreshStaticDs(){
        if(directoryDs.getItems().size()==0)
            refreshDirectoryDs();
        if(systemEntityDs.getItems().size()==0)
            refreshSystemEntityDs();
    }

    private void refreshDirectoryDs(){
        directoryDs.clear();
        if((!"".equals(urlField.getRawValue()))&&(!"".equals(passwordField.getRawValue())))
            importControllerService.getAllDirectories(urlField.getRawValue(),passwordField.getRawValue()).forEach(item->{
            TreeItem treeItem=metadata.create(TreeItem.class);
            treeItem.setName(item);
            directoryDs.addItem(treeItem);
        });
    }
    private void refreshSystemEntityDs(){
        systemEntityDs.clear();
        for (MetaClass metaClass : metadata.getSession().getClasses()) {
           // if(((Map<String,MetaPropertyImpl>)metaClass.getProperties()).entrySet().stream().map(item->item.getKey()).collect(Collectors.toList()).contains("extId")){
            if(metaClass.getProperties().stream().map(item->item.getName()).collect(Collectors.toList()).contains("extId")){

                TreeItem treeItem=metadata.create(TreeItem.class);
                treeItem.setName(metaClass.getName());
                systemEntityDs.addItem(treeItem);
            }
        }
    }
    private void refreshSelectedDirectoryDs(){
       Collection<TreeItem> selectedDirectories= twinColumn.getValue();
        selectedDirectoryDs.clear();
        selectedDirectories.forEach(item->{if(item!=null)selectedDirectoryDs.addItem(item);});
    }
    private void refreshAssociationDs(){
        associationDs.clear();
        if(selectedDirectoryDs.getItem()==null) return;
        String directoryName=selectedDirectoryDs.getItem().getName();

        try{
            Arrays.asList(
                    ((String)settingsService.getObjectValue(directoryKey+"-"+directoryName)).split(";"))
                    .stream().map(item->{
                TreeItem treeItem=metadata.create(TreeItem.class);

                treeItem.setStringValue1(item.substring(item.indexOf(".")+1,item.indexOf("-")));
                //удаляем поле из доступных
                entityFieldsDs.removeItem(getTreeItemByName(entityFieldsDs.getItems(),treeItem.getStringValue1()));

                treeItem.setStringValue2(item.substring(item.indexOf("-")+1));
                directoryFieldsDs.removeItem(getTreeItemByName(directoryFieldsDs.getItems(),treeItem.getStringValue2()));

                return treeItem;
            }).collect(Collectors.toList()).forEach(item->{
                associationDs.addItem(item);
            });
        }
        catch (Exception e){

        }

    }
    private void refreshDirectoryFieldsDs(String directoryName,List<String> usedInAssociation){
        directoryFieldsDs.clear();
        if((!"".equals(urlField.getRawValue()))&&(!"".equals(passwordField.getRawValue()))){
            importControllerService.getDirectoryFields(urlField.getRawValue(),passwordField.getRawValue(),directoryName).forEach(item->{
                if(usedInAssociation.contains(item)) return;
                TreeItem treeItem=metadata.create(TreeItem.class);
                treeItem.setName(item);
                directoryFieldsDs.addItem(treeItem);
            });
        }
    }
    private void refreshDirectoryFieldsDs(String directoryName){
        refreshDirectoryFieldsDs(directoryName,Collections.EMPTY_LIST);
    }
    private void refreshEntityFieldsDs(String entityName,List<String> usedInAssociation){
        entityFieldsDs.clear();

        //чтобы стандартные поля не отображались
        List<String> standardFields=Arrays.asList(StandardEntity.class.getDeclaredFields())
                .stream()
                .map(item1->item1.getName())
                .collect(Collectors.toList());
        standardFields.addAll(Arrays.asList(BaseUuidEntity.class.getDeclaredFields())
                .stream()
                .map(item1->item1.getName())
                .collect(Collectors.toList()));

        metadata.getSession().getClass(entityName).getProperties().forEach(item->{
            if((standardFields.contains(item.getName()))||(usedInAssociation.contains(item.getName()))) return; //||("extId".equals(item.getName()))
            TreeItem treeItem=metadata.create(TreeItem.class);
            treeItem.setName(item.getName());
            entityFieldsDs.addItem(treeItem);
        });
    }
    private void refreshEntityFieldsDs(String entityName){
        refreshEntityFieldsDs(entityName,Collections.EMPTY_LIST);
    }

    public void onCheckConnectionBtnClick() {
        settingsService.setValue(urlSettingsKey,urlField.getRawValue());
        settingsService.setValue(passwordSettingsKey,passwordField.getRawValue());
       if( importControllerService.getAllDirectories(urlField.getRawValue(),passwordField.getRawValue()).size()>0){
           checkConnectionLabel.setValue(getMessage("connection_established"));
       }
        else{
           checkConnectionLabel.setValue(getMessage("connection_error"));
       }
    }

    public void onDeleteAssociation(Component source) {
        deleteAssociation(selectedDirectoryDs.getItem().getName()
                ,systemEntityDs.getItem().getName()
                ,associationDs.getItem().getStringValue1()
                ,associationDs.getItem().getStringValue2());
        associationDs.removeItem(associationDs.getItem());
        
        if(selectedDirectoryDs.getItem()!=null) refreshDirectoryFieldsDs(selectedDirectoryDs.getItem().getName());
        if(systemEntityDs.getItem()!=null)refreshEntityFieldsDs(systemEntityDs.getItem().getName());
        refreshAssociationDs();
    }

    public void onAddAssociationBtnClick() {
        TreeItem treeItem=metadata.create(TreeItem.class);

        treeItem.setStringValue1(directoryFieldsDs.getItem().getName());
        directoryFieldsDs.removeItem(directoryFieldsDs.getItem());

        treeItem.setStringValue2(entityFieldsDs.getItem().getName());
        entityFieldsDs.removeItem(entityFieldsDs.getItem());

        associationDs.addItem(treeItem);
        checkAddAssociationBtnEnabled();

        addAssociation(selectedDirectoryDs.getItem().getName()
                ,systemEntityDs.getItem().getName()
                ,treeItem.getStringValue1()
                ,treeItem.getStringValue2());
    }

    private TreeItem getTreeItemByName(Collection<TreeItem> items,String name){
        TreeItem result=null;
        try{
            result=items.stream().filter(item->name.equals(item.getName())).findFirst().get();
        }
        catch (Exception e){

        }
        return result;
    }

    private void addAssociation(String directoryName,String entityName,String directoryField,String entityField){
        String existingAssociation=null;
        String associationElement=makeAssociationElement(entityName,directoryField,entityField);
        try{
            existingAssociation=(String)settingsService.getObjectValue(directoryKey+"-"+directoryName);
        }
        catch (Exception e){
            //значит записи сейчас нет
            settingsService.setValue(directoryKey+"-"+directoryName,associationElement+";");
            return;
        }
        if(existingAssociation.contains(associationElement))return;
        else settingsService.setValue(directoryKey+"-"+directoryName,existingAssociation+associationElement+";");
    }

    private void deleteAssociation(String directoryName,String entityName,String directoryField,String entityField){
        String existingAssociation=null;
        String associationElement=makeAssociationElement(entityName,entityField,directoryField);
        try{
            existingAssociation=(String)settingsService.getObjectValue(directoryKey+"-"+directoryName);
        }
        catch (Exception e){
            //значит записи сейчас нет
            return;
        }
        if(existingAssociation.contains(associationElement)) settingsService.setValue(directoryKey+"-"+directoryName,existingAssociation.replace(associationElement+";",""));
    }
    private void clearAssociations(String directoryName){
        String existingAssociation=null;
        try{
            existingAssociation=(String)settingsService.getObjectValue(directoryKey+"-"+directoryName);
        }
        catch (Exception e){
            //значит записи сейчас нет
            return;
        }
        settingsService.setValue(directoryKey+"-"+directoryName,"");
    }

    private String makeAssociationElement(String entityName,String directoryField,String entityField){
        return entityName+"."+entityField+"-"+directoryField;
    }

    private void addUsingDirectory(String directoryName){
        String usingDirectories=null;
        try{
            usingDirectories= (String) settingsService.getObjectValue(directoryListSettingsKey);
        }
        catch (Exception e){
            //значит записи сейчас нет
            settingsService.setValue(directoryListSettingsKey,directoryName+";");
            return;
        }
        if(usingDirectories.contains(directoryName)) return;
        else settingsService.setValue(directoryListSettingsKey,usingDirectories+directoryName+";");
    }

    private void removeUsingDirectory(String directoryName){
        String usingDirectories=null;
        try{
            usingDirectories= (String) settingsService.getObjectValue(directoryListSettingsKey);
        }
        catch (Exception e){
            //значит записи сейчас нет

            return;
        }
        if(usingDirectories.contains(directoryName))  settingsService.setValue(directoryListSettingsKey,usingDirectories.replace(directoryName+";",""));
    }

    private TreeItem getEntityForSelectedDirectory(){
        TreeItem result=null;
       try{
           String value=(String)settingsService.getObjectValue(directoryKey+"-"+selectedDirectoryDs.getItem().getName());
            result=getTreeItemByName(systemEntityDs.getItems(),value.substring(0,value.indexOf(".")));
       }
       catch (Exception e){
           return null;
       }
        return result;
    }
    private String getEntityForDirectory(String directoryName){
        try{
            String value=(String)settingsService.getObjectValue(directoryName);
            return getTreeItemByName(systemEntityDs.getItems(),value.substring(0,value.indexOf("."))).getName();
        }
        catch (Exception e){
            return null;
        }

    }
}