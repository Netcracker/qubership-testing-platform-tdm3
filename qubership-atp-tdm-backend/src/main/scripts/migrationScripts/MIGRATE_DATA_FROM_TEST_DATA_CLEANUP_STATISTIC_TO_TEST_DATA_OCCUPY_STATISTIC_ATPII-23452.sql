DO
$$
begin
    if exists
        (select 1 from information_schema.tables where table_name = 'test_data_cleanup_statistics')
    then
delete from
    test_data_cleanup_statistics
where table_name in (
    select distinct cs.table_name
    from test_data_table_catalog tc
             right join test_data_cleanup_statistics cs
                        on tc.table_name = cs.table_name
    where tc.table_name is null);

insert into test_data_occupy_statistic (
    row_id
    , table_name
    , occupied_by
    , occupied_date
    , project_id
    , system_id
    , table_title
    , created_when)
select
       gen_random_uuid()
     , cs.table_name
     , 'unknown'
     , cs.occupied_date
     , tc.project_id
     , tc.system_id
     , tc.table_title
     , cs.occupied_date
from test_data_cleanup_statistics cs, test_data_table_catalog tc
where cs.table_name = tc.table_name;
end if;
end
$$;
