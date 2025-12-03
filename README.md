# Simple Dialogue - Minecraft 对话系统模组

一个功能完整的 Minecraft 1.21 Fabric 对话系统模组，支持复杂的对话树、条件判断、动作执行和沉浸式交互体验。

## 核心功能

### 对话系统
- **对话树结构** - 支持多分支对话，可自由跳转
- **打字机效果** - 对话文本逐字显示，提升沉浸感
- **选项系统** - 支持多个对话选项，滚轮选择 + F键确认
- **JSON配置** - 所有对话内容通过JSON文件配置，易于编辑

### 绑定系统
- **实体绑定** - 将对话绑定到任意实体（NPC、生物等）
- **方块绑定** - 右键特定方块触发对话
- **区域触发** - 玩家进入指定区域自动触发对话

### QTE系统
- **倒计时选择** - 为对话选项设置时间限制
- **渐变进度条** - 100%-70%绿色，70%-30%黄色，30%-0%红色
- **超时处理** - 超时后可跳转到指定对话或关闭

### 条件与动作
**条件检查：**
- `has_tag` - 玩家必须拥有指定标签
- `missing_tag` - 玩家不能拥有指定标签
- `has_item` - 玩家背包需要指定物品和数量

**动作执行：**
- `add_tag` / `remove_tag` - 添加/移除玩家标签
- `give_item` / `remove_item` - 给予/扣除物品
- `run_command` - 执行任意Minecraft指令

### 管理工具
- **对话魔杖** - 可视化选择区域和方块
  - 区域模式：左键设置Pos1，右键设置Pos2
  - 方块模式：选择单个方块
  - 上/下方向键切换模式
- **完整命令系统** - `/sd` 命令管理所有功能
- **热重载** - `/sd reload` 无需重启即可更新配置

### 高级特性
- **一次性对话** - 对话只触发一次，使用标签记录
- **冷却系统** - 设置对话触发间隔时间
- **区域管理** - 创建、重命名、删除、传送到区域
- **调试工具** - 重置玩家对话历史，检查标签状态

## 命令列表

```
/sd help                                    - 显示帮助
/sd reload                                  - 重载配置
/sd list                                    - 列出所有对话ID

/sd bind <实体> <对话ID>                     - 绑定实体到对话
/sd unbind <实体>                           - 解除实体绑定

/sd block bind <对话ID>                     - 绑定选中方块
/sd block unbind                            - 解除方块绑定

/sd region create <区域ID> <对话ID>          - 创建区域
/sd region remove <区域ID>                  - 删除区域
/sd region rename <旧ID> <新ID>             - 重命名区域
/sd region set <区域ID> <对话ID>            - 修改区域对话
/sd region list                             - 列出所有区域
/sd region tp <区域ID>                      - 传送到区域
/sd region flag <区域ID> one_time <true|false>  - 设置一次性触发
/sd region flag <区域ID> cooldown <秒数>        - 设置冷却时间

/sd debug reset <玩家>                      - 重置玩家对话历史
/sd debug check <玩家>                      - 检查玩家标签
```

## 配置文件

配置文件位于 `config/simpledialogue/`：

- `dialogues.json` - 对话内容定义
- `bindings.json` - 实体绑定数据
- `regions.json` - 区域配置
- `block_bindings.json` - 方块绑定数据

### 对话配置示例

```json
[
  {
    "id": "welcome",
    "text": "欢迎来到我的世界！",
    "one_time": false,
    "cooldown": 0,
    "options": [
      {
        "text": "你好",
        "target_id": "greeting",
        "conditions": {
          "has_item": "minecraft:diamond",
          "count": 1
        },
        "actions": {
          "remove_item": "minecraft:diamond",
          "count": 1,
          "add_tag": "greeted"
        },
        "qte_timeout": 10,
        "timeout_target": "timeout_dialogue"
      }
    ]
  }
]
```

## 用户体验

- **沉浸式HUD** - 屏幕中央显示对话框，自动缩放和居中
- **可视化选择** - 魔杖高亮显示选中的区域和方块
- **智能交互** - 防抖动设计，避免误触发
- **聊天同步** - 对话内容同步显示在聊天框

## 技术特性

- Fabric API 支持
- Model Context Protocol (MCP) 兼容
- 客户端-服务端网络同步
- Mixin 注入实现滚轮事件拦截
- 完整的数据持久化

**版本：** 1.0.0  
**Minecraft：** 1.21  
**Fabric Loader：** 0.17.3+
