# 编辑项目

`POST /api/project/update`。需要平台菜单 `manage_project`。已关闭的项目也可以编辑。

```json
{"projectId":1,"appKey":"admin-app","name":"管理后台","description":"说明"}
```

字段规则同创建项目。成功 `data` 为 `null`。

错误：`100001` 参数不合法，`100003` 未登录，`100016` 无权访问，`100024` App Key 已存在，`100025` 项目不存在或已关闭。
