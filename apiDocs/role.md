# 角色与菜单权限

角色初始化只创建一个内置全局角色：`超级管理员`。其他角色通过角色接口创建，默认是项目角色。角色菜单关系保存在 `role_menu`。角色字段固定为名称、编码、描述、状态。

所有接口需要 `manage_role` 菜单权限，并使用 `POST` 和 JSON 请求体。

## 角色列表

`POST /api/role/list`，请求体 `{}`。

处理顺序：校验调用者菜单权限，读取全部角色，返回 `id`、`name`、`globalRole`、`builtIn`。

## 创建角色

`POST /api/role/create`

```json
{"name":"开发","code":"DEVELOPER","description":"开发人员","status":1}
```

新角色固定写入 `globalRole=false`、`builtIn=false`，初始没有菜单权限。

## 修改角色

`POST /api/role/update`

```json
{"roleId":2,"name":"开发","code":"DEVELOPER","description":"开发人员","status":1}
```

内置超级管理员角色不能修改。

## 删除角色

`POST /api/role/remove`

```json
{"roleId":2}
```

角色未被项目成员或账号角色使用时才允许删除。内置角色不能删除。

## 查询角色菜单

`POST /api/role/menu/list`

```json
{"roleId":2}
```

返回全量菜单和当前角色的 `menuIds`，客户端据此渲染权限选择器。

## 保存角色菜单

`POST /api/role/menu/update`

```json
{"roleId":2,"menuIds":[2,5]}
```

校验角色和菜单存在，并且每个子菜单的上级目录也在提交列表中，然后删除该角色原有关联，再写入去重后的新关联。只分配子菜单而缺少上级目录时返回 `100022`。超级管理员角色不可修改。新建菜单由服务器自动关联超级管理员，不需要客户端提交。

错误码：`100016` 无权访问，`100018` 角色不存在，`100019` 角色名称已存在，`100020` 内置角色不可修改，`100021` 角色正在使用，`100022` 菜单分配不合法，`100023` 角色编码已存在。
