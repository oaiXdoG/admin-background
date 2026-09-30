# 当前账号菜单

`POST /api/access/menu`

客户端登录后、切换项目后调用。服务器根据当前账号的全局角色、当前项目成员角色和角色菜单关联返回可用菜单。

请求头：

```http
Content-Type: application/json
Authorization: Bearer <token>
```

请求体：

```json
{"projectId": 1}
```

## 处理顺序

1. 校验令牌并查询账号，要求请求 `projectId` 与 `account.last_project_id` 相同，不一致返回 `100027`，不查询后续角色和菜单。请求项目还必须存在、未关闭，并且账号仍有项目访问关系。
2. 查询账号级角色。
3. 如果账号存在当前项目，加入该账号在当前项目中的 `project_user.role_id`。
4. 查询这些角色关联的菜单。
5. 只返回 `status=1` 的菜单，按父级、`sort`、`id` 排序。
6. `hidden=true` 的菜单仍返回，客户端不在侧栏展示，但仍可用于路由权限判断。

## 成功

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "parentId": 0,
      "menuType": 1,
      "name": "系统管理",
      "icon": "",
      "routeName": "manage",
      "routePath": "/manage",
      "status": 1,
      "hidden": false,
      "builtIn": false,
      "sort": 1
    }
  ]
}
```

## 失败

| code | message | 判断 |
| --- | --- | --- |
| 100001 | 请求参数不合法 | 请求体无法读取 |
| 100003 | 未登录 | 没有令牌，或令牌已失效 |
| 100005 | 不能切换到该项目 | 项目不存在、已关闭或账号无项目访问权限 |
| 100027 | 请求项目与当前项目不一致，请切换到正确项目后重试 | 请求的 projectId 与账号当前项目不一致，包括有当前项目但请求没有携带 projectId |

初始化时应先请求项目列表，再携带返回的 `currentProjectId` 请求本接口。仅在服务器账号没有当前项目时，允许 `{"projectId":null}`，用于获取创建首个项目所需的全局菜单权限。

## 服务器兜底

业务接口根据同一套角色菜单关联再次校验对应 `routeName`。客户端隐藏菜单不能绕过服务器权限。
