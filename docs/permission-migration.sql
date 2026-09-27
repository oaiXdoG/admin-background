-- 将旧 account.admin 管理员标记迁移为超级管理员角色。
-- 执行前确认数据库已备份；脚本可重复执行。

alter table role add column if not exists global_role boolean not null default false;
alter table role add column if not exists built_in boolean not null default false;
alter table role add column if not exists code varchar(64) not null default '';
alter table role add column if not exists description varchar(128) not null default '';
alter table role add column if not exists status smallint not null default 1;
alter table menu add column if not exists built_in boolean not null default false;

create table if not exists account_role (
    account_id bigint not null,
    role_id bigint not null,
    primary key (account_id, role_id),
    constraint fk_account_role_account foreign key (account_id) references account(id),
    constraint fk_account_role_role foreign key (role_id) references role(id)
);

create table if not exists role_menu (
    role_id bigint not null,
    menu_id bigint not null,
    primary key (role_id, menu_id),
    constraint fk_role_menu_role foreign key (role_id) references role(id),
    constraint fk_role_menu_menu foreign key (menu_id) references menu(id)
);

insert into role(name, global_role, built_in)
select '超级管理员', true, true
where not exists (select 1 from role where name = '超级管理员');

-- 固定超级管理员 id=1，其余已有角色按原 id 顺延一位。
-- 新库中的超级管理员已经是 1，因此只对旧库执行重排。
do $$
declare
    super_id bigint;
begin
    select id into super_id from role where name = '超级管理员';
    if super_id is not null and super_id <> 1 then
        alter table role_menu drop constraint fk_role_menu_role;
        alter table account_role drop constraint fk_account_role_role;
        alter table project_user drop constraint fk_project_user_role;
        update role_menu set role_id = -role_id;
        update account_role set role_id = -role_id;
        update project_user set role_id = -role_id;
        update role set id = -id;
        update role set id = case when name = '超级管理员' then 1 else -id + 1 end;
        update role_menu set role_id = case when role_id = -super_id then 1 else -role_id + 1 end;
        update account_role set role_id = case when role_id = -super_id then 1 else -role_id + 1 end;
        update project_user set role_id = case when role_id = -super_id then 1 else -role_id + 1 end;
        alter table role_menu add constraint fk_role_menu_role foreign key (role_id) references role(id);
        alter table account_role add constraint fk_account_role_role foreign key (role_id) references role(id);
        alter table project_user add constraint fk_project_user_role foreign key (role_id) references role(id);
    end if;
end $$;

update role set code = case name
    when '超级管理员' then 'SUPER_ADMIN'
    when '项目管理员' then 'PROJECT_ADMIN'
    when '开发' then 'DEVELOPER'
    when '前端' then 'FRONTEND'
    when '测试' then 'TESTER'
    when '运营' then 'OPERATOR'
    else 'ROLE_' || id::text end
where code = '';

update role set description = case name
    when '超级管理员' then '系统最高权限角色'
    when '项目管理员' then '项目级管理角色'
    when '开发' then '开发相关操作角色'
    when '前端' then '前端相关操作角色'
    when '测试' then '测试相关操作角色'
    when '运营' then '运营相关操作角色'
    else description end
where description = '';

insert into account_role(account_id, role_id)
select a.id, r.id from account a cross join role r
where a.admin = true and r.name = '超级管理员'
on conflict do nothing;

insert into role_menu(role_id, menu_id)
select r.id, m.id from role r cross join menu m
where r.name = '超级管理员'
on conflict do nothing;

update account set admin = false where admin = true;

-- 旧版本曾写入顶级 member 菜单；该页面已移除，后续如需成员功能由菜单管理重新创建。
delete from role_menu where menu_id in (select id from menu where route_name = 'member');
delete from menu where route_name = 'member';
