# 编辑项目

`POST /api/project/update`，请求头 `Content-Type: application/json`，使用原有 Authorization 令牌。

调用者必须拥有 `manage_project` 菜单权限。只允许编辑未关闭项目。

请求体：

```json
{"projectId":1,"appKey":"admin-app","name":"管理后台"}
```

`projectId` 为正整数。`appKey`、`name` 均必填，去除首尾空格后最长 64 字符；App Key 全局唯一，编辑时排除当前项目自身，关闭项目的 App Key 仍占用唯一值。项目 ID、创建时间保持不变。

成功返回：

```json
{"code":0,"message":"success","data":null}
```

错误：`100001` 参数不合法、`100003` 未登录、`100016` 无权访问、`100024` App Key 已存在、`100025` 项目不存在或已关闭。业务失败仍返回 HTTP `200`。
