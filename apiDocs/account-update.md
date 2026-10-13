# 编辑账号

`POST /api/account/update`

调用者必须拥有 `manage_user` 菜单权限。登录名不可修改。

请求体：

```json
{"accountId":2,"username":"爱丽丝","password":"新密码"}
```

`password` 可省略，省略时不修改密码；提交时不能为空白，最长 128 位。`username` 必填，去除首尾空白后最长 64 位。

成功 `data` 为 `null`。

错误：`100001` 参数不合法，`100003` 未登录，`100016` 无权访问，`100028` 账号不存在。
