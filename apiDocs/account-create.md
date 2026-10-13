# 创建账号

`POST /api/account/create`。需要平台菜单 `manage_user`。账号只能由平台创建，项目管理员通过「成员管理」把已有账号拉进项目。

```json
{"account":"alice","password":"123456","username":"爱丽丝","remark":"运营部"}
```

`account` 必填、去除首尾空白后最长 64 且唯一；`password` 不能为空白、最长 128，按 bcrypt 哈希保存；`username` 必填最长 64；`remark` 可选最长 128。新账号为启用状态，不挂项目、不绑定角色。

成功 `data`：`{"id":2,"account":"alice","username":"爱丽丝"}`。

错误：`100001` 参数不合法，`100003` 未登录，`100006` 登录名已存在，`100016` 无权访问。
