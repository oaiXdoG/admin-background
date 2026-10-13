# 账号项目关联（平台视角）

需要平台菜单 `manage_user`。项目关联对应 `project_user`，每个账号在每个项目中只有一个项目角色，角色必须属于该项目。

## 查询

`POST /api/account/project/list`，请求体 `{"accountId":2}`。返回未关闭项目的关联 `[{"projectId":1,"roleId":12}]`，按项目 ID 排序，没有关联时为空数组。

## 可选项目

`POST /api/account/project/options`，请求体 `{}`。返回全部未关闭项目，字段同项目管理列表（`memberCount` 为 0）。操作者不需要能进入这些项目。

## 项目可选角色

`POST /api/account/project/roles`，请求体 `{"projectId":1}`。返回该项目启用的项目角色，字段同角色列表。

## 保存

`POST /api/account/project/update`

```json
{"accountId":2,"memberships":[{"projectId":1,"roleId":12}]}
```

提交未关闭项目的完整关联列表，服务器替换该账号在未关闭项目上的关联；已关闭项目的关联保留。仍保留的项目沿用原加入时间。

每条关联必须满足：项目存在且未关闭；同一项目只出现一次；角色是该项目的、已启用的项目角色。任一条不满足时整体不保存。

错误：`100001` 参数不合法，`100003` 未登录，`100016` 无权访问，`100028` 账号不存在，`100029` 项目关联不合法。
