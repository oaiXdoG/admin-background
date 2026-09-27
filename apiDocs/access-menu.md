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
{}
```

## 处理顺序

1. 校验令牌并查询账号。
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

## 服务器兜底

业务接口根据同一套角色菜单关联再次校验对应 `routeName`。客户端隐藏菜单不能绕过服务器权限。
