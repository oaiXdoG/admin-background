# HTTP 统一响应格式

所有 HTTP 接口都使用以下 JSON 外层结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | `0` 表示业务成功；业务失败使用业务码。业务码只表示业务判断，不表示服务器内部故障 |
| `message` | String | 成功或失败的说明信息 |
| `data` | Object/null | 成功时的业务数据，失败时为 `null` |

## 状态规则

- 业务成功：HTTP 状态码为 `200`，`code` 为 `0`。
- 业务失败：HTTP 状态码仍为 `200`，`code` 使用业务码。业务码从 `100000` 起，只表示业务判断，定义和边界见 `apiDocs/error-code.md`。
- 业务层只通过 `BusinessException` 抛出业务判断。服务器内部故障不分配业务码，接口不把未知异常包装成错误码返回。

## 示例

业务成功：

```http
HTTP 200
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1
  }
}
```

业务失败：

```http
HTTP 200
```

```json
{
  "code": 100001,
  "message": "请求参数不合法",
  "data": null
}
```


