# UI плагинов (API 2.1)

Разрешение в манифесте: `"ui"`. Методы вызываются через `host->call(method, json)`.

## register_ui
```json
{"id":"translate_btn","slot":"chat_top_bar","kind":"icon_button","icon":"translate","title":"Перевод","order":0}
```
- `slot`: `chat_top_bar`, `chat_input`, `chat_list_header`, `settings_home`, `profile_screen`
- `kind`: `button`, `icon_button`, `switch`, `text`, `badge`
- необязательно: `subtitle`, `icon` (extension, bolt, build, code, copy, delete, download, edit, favorite, info, lock, notifications, search, settings, share, star, translate, send), `checked`, `visible`, `enabled`, `order`

## update_ui
Тот же JSON, но достаточно `id` и изменённых полей (например, `{"id":"translate_btn","checked":true}`).

## unregister_ui
`{"id":"translate_btn"}`. При выключении или удалении плагина элементы убираются сами.

## Событие
Нажатие или переключение приходит плагину событием `ui_event`:
```json
{"id":"translate_btn","slot":"chat_top_bar","chat_id":123,"message_id":0,"value":true}
```
`value` есть только у `switch`. Пункты в меню сообщения по-прежнему регистрируются через `register_action` (`where: "message_menu"`), событие `action`.
