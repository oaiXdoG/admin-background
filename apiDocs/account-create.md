# 创建账号

`POST /api/account/create`

调用者必须拥有 `manage_user` 菜单权限。新账号不挂项目、不绑定角色，也不会获得超级管理员角色。

请求体：

```json
{"account":"alice","password":"123456","username":"爱丽丝"}
```

执行顺序：校验令牌和 `manage_user` 权限，校验字段，检查登录名唯一性，创建账号并将迁移字段 `admin` 写为 `false`。

成功 `data`：

```json
{"id":2,"account":"alice","username":"爱丽丝"}
```

错误：`100001` 参数不合法，`100003` 未登录，`100006` 登录名已存在，`100016` 无权访问。
