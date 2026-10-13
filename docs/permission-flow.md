# 权限执行顺序

完整设计见 `docs/permission-redesign.md`。

## 三类角色

| 类型 | `role_type` | 所属 | 分配方式 | 可分配菜单 |
| --- | --- | --- | --- | --- |
| 平台角色 | 1 | 无 | `account_role` | 平台菜单（`scope=1`） |
| 角色模板 | 2 | 无 | 不直接分配，复制为项目角色 | 项目菜单（`scope=2`） |
| 项目角色 | 3 | `project_id` | `project_user` | 项目菜单（`scope=2`） |

内置平台角色「超级管理员」（`SUPER_ADMIN`）可以切换所有未关闭项目，在任何项目中拥有全部启用的项目菜单，不需要成为成员。

## 流程

1. 登录校验账号状态和 bcrypt 密码，建立会话令牌。
2. 客户端调用 `/api/project/list` 确定会话当前项目，再携带 `currentProjectId` 调用 `/api/access/menu`。
3. 菜单 = 平台角色的平台菜单 + 当前项目中项目角色的项目菜单（超级管理员为全部项目菜单）。只返回启用菜单。
4. 平台接口调用 `PermissionService.requirePlatformMenu(authorization, routeName)`，只认平台角色。
5. 项目接口调用 `PermissionService.requireProjectMenu(authorization, projectId, routeName)`：请求项目必须等于会话当前项目，项目未关闭，账号是成员或超级管理员，并拥有目标菜单。
6. 项目角色无论怎么配置都拿不到平台菜单，因此不能通过项目角色获得系统管理权限。

## 接口权限

| 接口 | 权限菜单 |
| --- | --- |
| `/api/project/manage/list`、`create`、`update`、`close`、`reopen` | 平台 `manage_project` |
| `/api/account/*` | 平台 `manage_user` |
| `/api/role/*` | 平台 `manage_role` |
| `/api/role-template/*` | 平台 `manage_role_template` |
| `/api/menu/*` | 平台 `manage_menu` |
| `/api/system/setting/update` | 平台 `manage_setting` |
| `/api/project/member/*` | 项目 `project_member` |
| `/api/project/role/*` | 项目 `project_role` |
| `/api/login`、`/api/logout`、`/api/project/list`、`/api/project/switch`、`/api/access/menu`、`/api/profile/*` | 登录即可 |
| `/api/system/setting/get` | 不需要登录 |
