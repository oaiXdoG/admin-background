# 角色模板

需要平台菜单 `manage_role_template`。模板（`role_type=2`）由平台维护，带一套默认的项目菜单（只能分配 `scope=2` 的菜单），不直接分配给账号。

模板在新建项目或「从模板导入」时**复制**为项目角色。复制后的项目角色与模板互不影响：修改模板不改变已复制的角色，删除模板时已复制角色的 `templateId` 清空。

接口与字段规则同平台角色（见 `role.md`），路径前缀为 `/api/role-template/`：`list`、`create`、`update`、`remove`、`menu/list`、`menu/update`。
