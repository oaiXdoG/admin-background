# 项目角色

需要当前项目中的项目菜单 `project_role`；超级管理员在任何项目中都可以使用。项目角色（`role_type=3`）属于一个项目，只能分配项目菜单，通过 `project_user` 分配给项目成员。

所有请求都携带 `projectId`，必须等于会话当前项目，否则返回 `100027`。只能操作该项目自己的角色。

接口与字段规则同平台角色（见 `role.md`），路径前缀为 `/api/project/role/`：`list`、`create`、`update`、`remove`、`menu/list`、`menu/update`，请求体在原有字段上增加 `projectId`。列表返回 `templateName` 表示来源模板。

## 从模板导入

`POST /api/project/role/import`，请求体 `{"projectId":1,"templateIds":[3,6]}`。把模板连同菜单复制为本项目角色；与本项目已有角色同名或同编码的模板跳过。成功 `data` 为导入的数量。

错误：另有 `100005` 不能访问该项目，`100027` 项目不一致。
