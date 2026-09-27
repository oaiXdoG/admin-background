# 关闭项目

`POST /api/project/close`，请求头 `Content-Type: application/json`，使用原有 Authorization 令牌。

调用者必须拥有 `manage_project` 菜单权限。

请求体：

```json
{"projectId":1}
```

`projectId` 为正整数。关闭后 `project.closed` 为 `true`；保留项目数据及 `project_user` 成员关联。该项目不再出现在项目列表或切换列表中，不能再次切换到它。清除所有账号指向该项目的 `last_project_id`；下次读取项目列表时选择第一个可用项目，没有项目则返回 `null`。

成功返回：

```json
{"code":0,"message":"success","data":null}
```

错误：`100001` 参数不合法、`100003` 未登录、`100016` 无权访问、`100025` 项目不存在或已关闭。业务失败仍返回 HTTP `200`。
