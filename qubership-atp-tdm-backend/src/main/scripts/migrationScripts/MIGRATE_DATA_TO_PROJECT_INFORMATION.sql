DO
$$
begin
        if exists
            (select 1 from information_schema.tables where table_name = 'project_information')
        then
            insert into project_information (project_id, time_zone, date_format, time_format)
select distinct(project_id), 'GMT+03:00', 'd MMM yyyy', 'hh:mm:ss a' from test_data_table_catalog;
end if;
end
$$;
