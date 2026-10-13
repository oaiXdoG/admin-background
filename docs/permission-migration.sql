-- 平台 / 模板 / 项目三层角色改造的存量数据迁移，见 docs/permission-redesign.md。
-- 执行前先备份数据库；整个脚本放在一个事务里执行，任一步失败全部回滚。只执行一次。
-- 明文密码只改列名，服务启动时由 PasswordUpgradeService 统一转为 bcrypt。

-- 项目：closed 改为 status，补描述、App Key 和时间
alter table project add column description varchar(128) not null default '';
alter table project add column status smallint not null default 1;
update project set status = case when closed then 2 else 1 end;
alter table project drop column closed;
update project set created_at = (extract(epoch from clock_timestamp()) * 1000)::bigint where created_at is null;
alter table project alter column created_at set not null;
update project set app_key = substr(md5(random()::text || id::text), 1, 32) where app_key is null or app_key = '';
alter table project alter column app_key set not null;
alter table project add column updated_at bigint;
update project set updated_at = created_at;
alter table project alter column updated_at set not null;

-- 账号：密码列改名，增加状态、备注、登录时间和创建时间，删除迁移字段 admin
alter table account rename column password to password_hash;
alter table account alter column password_hash type varchar(100);
alter table account add column status smallint not null default 1;
alter table account add column remark varchar(128) not null default '';
alter table account add column last_login_at bigint;
alter table account add column created_at bigint;
alter table account add column updated_at bigint;
update account set created_at = (extract(epoch from clock_timestamp()) * 1000)::bigint, updated_at = (extract(epoch from clock_timestamp()) * 1000)::bigint;
alter table account alter column created_at set not null;
alter table account alter column updated_at set not null;
alter table account drop column admin;

-- 菜单：增加范围，系统管理树为平台，其余为项目
alter table menu add column scope smallint;
alter table menu add column created_at bigint not null default 0;
alter table menu add column updated_at bigint not null default 0;
with recursive tree as (
    select id, case when route_name = 'manage' then 1 else 2 end as scope from menu where parent_id is null
    union all
    select m.id, tree.scope from menu m join tree on m.parent_id = tree.id
)
update menu set scope = tree.scope from tree where menu.id = tree.id;
alter table menu alter column scope set not null;
insert into menu (id, parent_id, menu_type, scope, name, icon, route_name, route_path, status, hidden, built_in, sort_order) values
    (15, 10, 2, 1, '角色模板', '', 'manage_role_template', '/manage/role-template', 1, false, false, 4),
    (300, null, 1, 2, '项目设置', '', 'project', '/project', 1, false, false, 90),
    (301, 300, 2, 2, '成员管理', '', 'project_member', '/project/member', 1, false, false, 1),
    (302, 300, 2, 2, '角色管理', '', 'project_role', '/project/role', 1, false, false, 2);
update menu set sort_order = 5 where route_name = 'manage_menu';

-- 角色：global_role 改为 role_type。超级管理员为平台角色，其余现有角色转为模板
alter table role drop constraint uk_role_name;
alter table role add column role_type smallint;
alter table role add column project_id bigint;
alter table role add column template_id bigint;
alter table role add column created_at bigint;
alter table role add column updated_at bigint;
update role set role_type = case when global_role then 1 else 2 end,
    created_at = (extract(epoch from clock_timestamp()) * 1000)::bigint,
    updated_at = (extract(epoch from clock_timestamp()) * 1000)::bigint;
alter table role drop column global_role;
alter table role alter column role_type set not null;
alter table role alter column created_at set not null;
alter table role alter column updated_at set not null;
alter table role add constraint fk_role_project foreign key (project_id) references project (id);

-- 平台角色只保留平台菜单；模板只保留项目菜单
delete from role_menu rm using role r, menu m
where rm.role_id = r.id and rm.menu_id = m.id and ((r.role_type = 1 and m.scope = 2) or (r.role_type = 2 and m.scope = 1));
insert into role_menu (role_id, menu_id)
select r.id, m.id from role r cross join menu m
where r.role_type = 1 and r.code = 'SUPER_ADMIN' and m.scope = 1
  and not exists (select 1 from role_menu x where x.role_id = r.id and x.menu_id = m.id);

-- 「项目管理员」模板补上项目设置菜单
insert into role_menu (role_id, menu_id)
select r.id, m.id from role r cross join menu m
where r.role_type = 2 and r.code = 'PROJECT_ADMIN' and m.id in (300, 301, 302)
  and not exists (select 1 from role_menu x where x.role_id = r.id and x.menu_id = m.id);

-- 成员正在使用的模板复制成对应项目的项目角色，成员改为指向复制出的角色
insert into role (role_type, project_id, template_id, name, code, description, status, built_in, created_at, updated_at)
select distinct 3, pu.project_id, r.id, r.name, r.code, r.description, r.status, false, r.created_at, r.updated_at
from project_user pu join role r on r.id = pu.role_id
where r.role_type = 2;
insert into role_menu (role_id, menu_id)
select pr.id, rm.menu_id from role pr join role_menu rm on rm.role_id = pr.template_id
where pr.role_type = 3;
update project_user pu set role_id = pr.id
from role pr
where pr.role_type = 3 and pr.project_id = pu.project_id and pr.template_id = pu.role_id;

-- 还没有「项目管理员」的项目补一个，拥有全部启用的项目菜单
insert into role (role_type, project_id, name, code, description, status, built_in, created_at, updated_at)
select 3, p.id, '项目管理员', 'PROJECT_ADMIN', '项目级管理角色', 1, false, p.created_at, p.created_at
from project p
where not exists (select 1 from role r where r.role_type = 3 and r.project_id = p.id and r.code = 'PROJECT_ADMIN');
insert into role_menu (role_id, menu_id)
select r.id, m.id from role r cross join menu m
where r.role_type = 3 and r.code = 'PROJECT_ADMIN' and r.template_id is null and m.scope = 2 and m.status = 1
  and not exists (select 1 from role_menu x where x.role_id = r.id);

alter table role add constraint ck_role_project check ((role_type = 3) = (project_id is not null));
create unique index uk_role_name on role (role_type, coalesce(project_id, 0), name);
create unique index uk_role_code on role (role_type, coalesce(project_id, 0), code);

-- 账号级角色只能是平台角色
delete from account_role ar using role r where ar.role_id = r.id and r.role_type <> 1;

-- 项目成员加入时间
alter table project_user add column created_at bigint;
update project_user set created_at = (extract(epoch from clock_timestamp()) * 1000)::bigint;
alter table project_user alter column created_at set not null;
