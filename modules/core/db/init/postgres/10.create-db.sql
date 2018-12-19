-- begin IMPORT1CSTP_SETTINGS
create table IMPORT1CSTP_SETTINGS (
    ID uuid,
    VERSION integer not null,
    CREATE_TS timestamp,
    CREATED_BY varchar(50),
    UPDATE_TS timestamp,
    UPDATED_BY varchar(50),
    DELETE_TS timestamp,
    DELETED_BY varchar(50),
    --
    KEY_ varchar(50) not null,
    TEXT varchar(1000),
    BOOLEAN_VALUE boolean,
    --
    primary key (ID)
)^
-- end IMPORT1CSTP_SETTINGS
