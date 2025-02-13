# **zoChat**
![GitHub](https://img.shields.io/github/license/ZorahM/zoChat) ![Version](https://img.shields.io/badge/version-1.0.2-blue)

**zoChat** — мощный плагин для улучшения чата на Minecraft-серверах. Поддерживает кастомное форматирование, упоминания, личные сообщения и интеграцию с LuckPerms для отображения префиксов и суффиксов.

---

## 📜 **Особенности**
- **Упоминания игроков** с подсветкой и уведомлениями.
- **Кастомный формат сообщений** (с поддержкой цветов HEX и MiniMessage).
- **Личные сообщения** с полностью настраиваемым стилем.
- **Интеграция с LuckPerms** для отображения префиксов/суффиксов.
- Антиспам, фильтр запрещённых слов и возможность очистки чата.
- Поддержка всех современных версий Minecraft (1.19+).
- **Action Bar уведомления** при упоминаниях.
- **MySQL/SQLite логирование чата** (опционально).

---

## 🔧 **Установка**
1. Скачайте последнюю версию `zoChat.jar` из [релизов](https://github.com/ZorahM/zoChat/releases).
2. Поместите файл в папку `plugins` вашего сервера.
3. Перезапустите сервер.
4. Настройте плагин через файл `config.yml`.

---

## 📁 **Конфигурация**

После запуска плагина создаётся файл `config.yml`. Вот пример настройки:
```yaml
chat-format: "<#d45079>SkyPvP</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{player} <#c0c0c0>›</#c0c0c0> {message}"

spam-cooldown: 3

private-messages:
  format: "<#d45079>SkyPvP</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>{player} <#c0c0c0>›</#c0c0c0> {message}"
  reply-format: "<#d45079>SkyPvP</#d45079> <#c0c0c0>•</#c0c0c0> <#fcfcfc>Вы <#c0c0c0>›</#c0c0c0> {message}"

mention:
  format: "<yellow><bold>@{player}</bold></yellow>" # Формат подсветки упоминания
  sound: "ENTITY_EXPERIENCE_ORB_PICKUP"             # Звук при упоминании
  message: "<yellow>Тебя упомянули в чате!</yellow>" # Уведомление через Action Bar

database:
  type: "sqlite" # "mysql" или "sqlite"
  host: "localhost"
  port: 3306
  database: "minecraft_chat"
  username: "root"
  password: "password"
```
## 📜 **Команды и права**

### **Команды:**

| Команда               | Описание                                      | Права           |
|-----------------------|----------------------------------------------|-----------------|
| `/chat reload`        | Перезагрузить конфигурацию плагина.           | `chat.admin`    |
| `/chat clear`         | Очистить чат для всех игроков.                | `chat.admin`    |
| `/msg <игрок> <текст>` | Отправить личное сообщение.                   | `chat.private`  |
| `/reply <текст>`      | Ответить на последнее личное сообщение.       | `chat.private`  |

---

### **Права:**

| Право           | Описание                                      | По умолчанию     |
|------------------|----------------------------------------------|------------------|
| `chat.admin`     | Доступ к администраторским командам.          | Только OP        |
| `chat.private`   | Возможность отправлять личные сообщения.      | Включено для всех|
| `chat.mention`   | Возможность упоминать игроков через `@`.      | Включено для всех|

---

## 📚 **API Интеграция**

Плагин интегрируется с:
- **LuckPerms** для работы с префиксами и суффиксами.
- **Adventure API** для цветного форматирования чата.
- **MySQL/SQLite** для логирования.

---

## 🚀 **Планы на будущее**

- Интеграция с Discord для синхронизации чата.
- Автосообщения в чат (реклама, напоминания).
- Система голосований `/poll`.

---

## 🛠 **Разработчик**

- **Автор**: ZorahM
- **Сайт проекта**: [https://zorahmproject.ru](https://zorahmproject.ru)
