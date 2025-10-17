create table project_information (
    project_id UUID not null primary key,
    time_zone varchar not null default 'GMT+03:00',
    date_format varchar not null default 'd MMM yyyy',
    time_format varchar not null default 'hh:mm:ss a'
);
