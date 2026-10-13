# 创建项目

`POST /api/project/create`。需要平台菜单 `manage_project`。

```json
{"appKey":"admin-app","name":"管理后台","description":"","templateIds":[2,6]}
```

`appKey` 必填，去除首尾空白后最长 64，区分大小写且全局唯一；`name` 必填最长 64；`description` 可选最长 128；`templateIds` 为要复制的角色模板 ID，可为空数组或省略。

处理顺序：校验权限和字段，检查 App Key 唯一，创建项目（状态为正常），生成项目角色「项目管理员」（`PROJECT_ADMIN`，拥有全部启用的项目菜单），再按 `templateIds` 复制模板为项目角色（与已有角色同名或同编码的模板跳过）。创建者可以访问新项目时，会话当前项目切换到新项目。

成功 `data`：`projectId`、`appKey`、`name`、`description`、`createdAt`、`currentProjectId`。

错误：`100001` 参数不合法，`100003` 未登录，`100016` 无权访问，`100018` 模板不存在，`100024` App Key 已存在。
