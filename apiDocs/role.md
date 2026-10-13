# 平台角色

需要平台菜单 `manage_role`。平台角色（`role_type=1`）不属于任何项目，通过 `account_role` 分配给账号，只能分配平台菜单（`scope=1`）。内置「超级管理员」不能修改、删除。

角色模板见 `role-template.md`，项目角色见 `project-role.md`。三类角色的接口字段与规则相同，只是权限菜单、所属项目和可分配菜单范围不同。

## 接口

| 接口 | 请求体 | 说明 |
| --- | --- | --- |
| `/api/role/list` | `{}` | 返回平台角色列表 |
| `/api/role/create` | `{"name","code","description","status"}` | 返回新角色 |
| `/api/role/update` | `{"roleId","name","code","description","status"}` | 返回修改后的角色 |
| `/api/role/remove` | `{"roleId"}` | 角色仍被账号或项目成员使用时拒绝 |
| `/api/role/menu/list` | `{"roleId"}` | 返回可分配范围内的全部菜单 `menus` 和已分配的 `menuIds` |
| `/api/role/menu/update` | `{"roleId","menuIds"}` | 整体替换角色菜单 |

## 字段规则

- `name` 必填最长 32，`code` 只允许大写字母、数字和下划线并以字母开头，最长 64；二者在同一类型、同一项目内唯一。
- `description` 最长 128，`status` 为 1 启用或 2 停用。
- 分配菜单：菜单必须存在、在可分配范围内，并且每个子菜单的上级目录也在提交列表中，否则返回 `100022`。

角色返回字段：`id`、`roleType`、`projectId`、`templateId`、`templateName`、`name`、`code`、`description`、`status`、`builtIn`、`createdAt`。为空的字段省略。

错误：`100001` 参数不合法，`100003` 未登录，`100016` 无权访问，`100018` 角色不存在，`100019` 角色名称已存在，`100020` 内置角色不能修改或删除，`100021` 角色正在使用，`100022` 菜单分配不合法，`100023` 角色编码已存在。

## 下拉选项

只返回启用的角色，各自只校验所在页面的权限：

| 接口 | 请求体 | 权限 | 返回 |
| --- | --- | --- | --- |
| `/api/account/role/options` | `{}` | 平台 `manage_user` | 平台角色，用于给账号分配平台角色 |
| `/api/project/templates` | `{}` | 平台 `manage_project` | 角色模板，用于新建项目时勾选 |
| `/api/project/role/templates` | `{"projectId"}` | 项目 `project_role` | 角色模板，用于从模板导入 |
| `/api/project/member/roles` | `{"projectId"}` | 项目 `project_member` | 本项目角色，用于成员管理 |
