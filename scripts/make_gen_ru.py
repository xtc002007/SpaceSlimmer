# -*- coding: utf-8 -*-
"""
Generates scripts/locales/gen_ru.py with all 501 keys and runs it.
"""
import os
import json
import xml.etree.ElementTree as ET

tree_base = ET.parse('android/app/src/main/res/values/strings.xml')
all_keys = [elem.attrib['name'] for elem in tree_base.getroot().findall('string')]

RU_TRANSLATIONS = {
    # Core app & Home
    "app_name": "SpaceSlimmer",
    "app_name_en": "SpaceSlimmer",
    "home_title": "SpaceSlimmer",
    "home_subtitle": "Сжимайте видео, фото и аудио без потери воспоминаний",
    "home_pick_title": "Сжать файлы",
    "photo_importing": "Импорт…",
    "photo_preset_title": "Пресет фото",
    "photo_selected_count": "Выбрано %1$d фото",
    "photo_preset_quality": "Качество",
    "photo_preset_format": "Формат вывода",
    "photo_format_jpeg": "JPEG",
    "photo_format_webp": "WebP",
    "photo_format_png": "PNG",
    "photo_start_compress": "Начать сжатие",
    "photo_jobs_title": "Задачи фото",
    "photo_jobs_summary": "Готово %1$d · Пропущено %2$d · Ошибок %3$d · Сэкономлено %4$s",
    "photo_view_results": "Посмотреть результаты",
    "photo_saved_bytes": "Сэкономлено %1$s",
    "photo_result_title": "Результаты сжатия фото",
    "photo_no_results": "Нет результатов",
    "photo_total_saved": "Всего сэкономлено: %1$s",
    "photo_compare": "До / После",
    "photo_save_gallery": "Сохранить в галерею",
    "photo_saved_to_gallery": "%1$d сохранено в альбом SpaceSlimmer",
    "photo_replace_gallery": "Заменить оригиналы",
    "photo_replaced_count": "Заменено %1$d фото",
    "photo_compare_title": "До / После",
    "photo_before": "До",
    "photo_after": "После",
    "video_preset_title": "Пресет видео",
    "audio_preset_title": "Пресет аудио",
    "audio_preset_format": "Формат вывода",
    "audio_format_aac": "AAC (по умолчанию)",
    "audio_format_opus": "Opus",
    "audio_format_aac_default_note": "AAC имеет наилучшую совместимость. Opus более компактен, но воспроизводится не везде.",
    "audio_format_opus_unavailable": "В этой версии нет кодировщика Opus. Выберите AAC или пересоберите приложение с поддержкой Opus.",
    "compare_slider_handle": "Ползунок: перемещайте для сравнения «до» и «после»",
    "compare_image_before": "Исходное изображение",
    "compare_image_after": "Сжатое изображение",
    "radar_item_thumbnail": "Миниатюра %1$s",
    "vault_item_thumbnail": "Оригинал в сейфе %1$s",
    "media_selected_count": "Выбрано %1$d файлов",
    "media_start_compress": "Начать сжатие",
    "media_jobs_title": "Задачи сжатия",
    "media_jobs_summary": "Готово %1$d · Пропущено %2$d · Ошибок %3$d · Сэкономлено %4$s",
    "media_result_title": "Результаты",
    "media_no_results": "Нет результатов",
    "media_total_saved": "Всего сэкономлено: %1$s",
    "media_save_gallery": "Сохранить в галерею",
    "media_saved_to_gallery": "%1$d файлов сохранено в альбом SpaceSlimmer",
    "media_replace_gallery": "Заменить оригиналы",
    "media_replaced_count": "Заменено %1$d файлов",
    "video_compare": "Сравнить видео",
    "video_compare_title": "Сравнение видео",
    "preset_image_max": "Максимальное",
    "preset_image_high": "Высокое",
    "preset_image_small": "Компактное",
    "preset_video_high": "Высокое",
    "preset_video_balanced": "Сбалансированное",
    "preset_video_small": "Компактное",
    "preset_audio_speech": "Голос",
    "preset_audio_high": "Музыка (высокое)",
    "preset_audio_small": "Музыка (компактное)",
    "job_succeeded": "Успешно, сэкономлено %1$s",
    "job_skipped": "Пропущено: %1$s",
    "job_failed": "Ошибка: %1$s",
    "job_running": "Обработка %1$d%%",
    "action_back": "Назад",
    "action_pause": "Пауза",
    "action_resume": "Продолжить",
    "action_cancel": "Отмена",
    "action_retry": "Повторить",
    "about_title": "О приложении",
    "about_core_version": "Версия движка",
    "about_preset_version": "Версия пресетов",
    "about_data_dir": "Каталог данных",
    "about_tmp_dir": "Временный каталог",
    "about_vault_dir": "Каталог сейфа",
    "about_privacy": "Медиафайлы обрабатываются исключительно локально на устройстве и никогда не выгружаются в сеть.",
    "pick_video": "Видео",
    "pick_photo": "Фото",
    "pick_audio": "Аудио",
    "tab_home": "Главная",
    "tab_vault": "Сейф",
    "tab_settings": "Профиль",
    "vault_title": "Сейф",
    "vault_usage": "Занято: %1$s",
    "vault_hint": "Оригиналы сохраняются здесь перед заменой. Напоминание через 7 дней; автоматическое удаление отключено.",
    "vault_empty": "Сейф пуст",
    "vault_item_meta": "%1$s · истекает %2$s",
    "vault_delete": "Удалить копию",
    "vault_restore": "Восстановить",
    "vault_restored": "Восстановлено в исходное место %1$s",
    "vault_restored_path": "Помечено как восстановленное: %1$s",
    "vault_already_restored": "Уже восстановлено",
    "settings_title": "Профиль",
    "settings_skip": "Умный пропуск",
    "settings_skip_enabled": "Включить пропуск",
    "settings_video_skip_ratio": "Коэффициент пропуска видео",
    "settings_video_skip_abs": "Порог пропуска видео (байт)",
    "settings_image_skip_ratio": "Коэффициент пропуска фото",
    "settings_image_skip_abs": "Порог пропуска фото (байт)",
    "settings_keep_gps": "Сохранять данные GPS",
    "settings_vault_days": "Срок хранения в сейфе (дней)",
    "settings_parallel": "Параллельный режим",
    "settings_parallel_eco": "Энергосбережение",
    "settings_parallel_auto": "Авто",
    "settings_parallel_fast": "Быстро",
    "settings_default_video": "Пресет видео по умолчанию",
    "settings_default_image": "Пресет фото по умолчанию",
    "settings_default_audio": "Пресет аудио по умолчанию",
    "settings_default_image_format": "Формат фото по умолчанию",
    "settings_default_compat": "Совместимость по умолчанию",
    "settings_language": "Язык (Language)",
    "settings_save_failed": "Не удалось сохранить настройки.",
    "radar_title": "Радар очистки",
    "radar_tab_photo": "Очистка фото",
    "radar_tab_video": "Очистка видео",
    "radar_tab_audio": "Очистка аудио",
    "radar_empty_title": "Всё чисто!",
    "radar_empty_subtitle": "В этой категории нет файлов для очистки.",
    "radar_scanned_count": "Найдено %1$d элементов, освободится примерно %2$s",
    "radar_scanning": "Сканирование…",
    "radar_start_scan": "Сканировать устройство",
    "radar_clean_selected": "Очистить выбранное (%1$s)",
    "radar_select_all": "Выбрать все",
    "radar_deselect_all": "Снять выбор",
    "radar_clean_confirm_title": "Подтверждение очистки",
    "radar_clean_confirm_message": "Вы уверены, что хотите удалить выбранные %1$d файлов? Будет освобождено %2$s.",
    "radar_clean_success": "Очистка завершена! Освобождено %1$s.",
    "radar_junk_cache": "Кэш-файлы",
    "radar_junk_temp": "Временные файлы",
    "radar_junk_apk": "Устаревшие APK",
    "radar_junk_thumbnails": "Остаточные миниатюры",
    "radar_junk_empty_dirs": "Пустые папки",
    "radar_junk_large_files": "Большие файлы",
    "radar_junk_duplicates": "Дубликаты файлов",
    "radar_junk_screenshots": "Скриншоты",
    "radar_junk_blur": "Размытые фото",
    "radar_junk_similar": "Похожие фото",
    "radar_permission_title": "Требуется разрешение на доступ",
    "radar_permission_message": "Для сканирования остаточных файлов радару требуется доступ ко всем файлам.",
    "radar_grant_permission": "Предоставить доступ",
    "radar_cancel": "Отмена",
    "batch_manage": "Пакетное управление",
    "vault_clear_all_btn": "Очистить все",
    "batch_selected_ratio": "Выбрано %1$d/%2$d",
    "batch_select_all": "Выбрать все",
    "batch_deselect_all": "Снять выбор",
    "batch_delete_selected": "Удалить (%1$d)",
    "batch_exit": "Выход",
    "vault_clear_all_title": "Опасная операция: Очистить сейф",
    "vault_clear_all_warning": "⚠️ 【Крайне важное предупреждение】Это действие необратимо!",
    "vault_clear_all_body": "Очистка сейфа навсегда удалит все %1$d резервных копий оригиналов (%2$s).\n\nПосле этого восстановить оригинальные файлы будет невозможно! Подтверждаете?",
    "vault_clear_all_confirm": "Подтвердить полную очистку",
    "vault_cleared_all": "Все резервные копии в сейфе удалены",
    "vault_batch_delete_title": "Удалить выбранные копии?",
    "vault_batch_delete_warning": "⚠️ 【Внимание】Выбранные %1$d резервных копий будут удалены навсегда!",
    "vault_batch_delete_body": "После удаления эти файлы невозможно будет восстановить в галерею. Продолжить?",
    "vault_batch_deleted": "Удалено %1$d копий",
    "vault_original_path_default": "Исходный путь в галерее",
    "vault_original_file_default": "Исходный файл",
    "err_backup_engine_not_found": "Движок резервного копирования не найден",
    "vault_restored_to_gallery": "Исходный файл успешно восстановлен в галерею",
    "vault_restore_failed": "Сбой восстановления: %1$s",
    "vault_permission_needed": "Требуются разрешения для восстановления файла в галерею",
    "vault_compressed_not_found": "Путь к сжатому файлу не найден",
    "vault_open_failed": "Не удалось открыть файл: %1$s",
    "vault_extend_failed": "Сбой продления: %1$s",
    "vault_deleted": "Копия удалена",
    "vault_delete_failed": "Сбой удаления: %1$s",
    "vault_item_path": "Путь: %1$s",
    "vault_item_time_size": "Дата: %1$s  ·  %2$s",
    "vault_restore_confirm_title": "Восстановить копию?",
    "vault_restore_confirm_body": "Восстановить исходный файл «%1$s» в системную галерею? Вы сможете найти его в альбомах.",
    "vault_restore_confirm_ok": "Восстановить",
    "vault_restore_action": "Восстановить",
    "vault_delete_confirm_title": "Удалить резервную копию навсегда?",
    "vault_delete_confirm_body": "После удаления копия оригинала «%1$s» будет безвозвратно удалена и её нельзя будет восстановить! Подтверждаете?",
    "vault_delete_confirm_ok": "Удалить навсегда",
    "vault_retention_days_format": "Хранение %1$d дн.",
    "vault_extend_7_days": "+7 дней",
    "vault_extended_toast": "Продлено на 7 дней, истекает: %1$s",
    "vault_item_original_missing": "Исходный файл не найден на диске",
    "vault_view_compressed": "Сжатый файл",
    "history_batch_manage": "Пакетное управление",
    "history_clear_records": "Очистить историю",
    "history_empty_title": "История сжатия пуста",
    "history_empty_subtitle": "Обработанные файлы появятся здесь после сжатия.",
    "history_badge_pending_save": "Ожидает сохранения",
    "history_clear_confirm_title": "Очистить всю историю?",
    "history_clear_confirm_body": "Будут удалены только записи журнала. Ваши сжатые файлы на диске останутся нетронутыми.",
    "history_clear_confirm_ok": "Очистить",
    "history_cleared_toast": "История сжатия очищена",
    "history_batch_delete_title": "Удалить записи?",
    "history_batch_delete_body": "Удалить выбранные %1$d записей? Файлы на устройстве затронуты не будут.",
    "history_batch_delete_ok": "Удалить выбранные",
    "history_batch_deleted_toast": "Удалено %1$d записей",
    "history_detail_title": "Сведения о сжатии",
    "history_pending_save_tag": "Не сохранено в галерею",
    "history_view_results_btn": "Посмотреть варианты сохранения",
    "history_source_file": "Исходный файл:",
    "history_target_file": "Сжатый файл:",
    "history_compress_time": "Время сжатия:",
    "history_duration": "Длительность:",
    "history_size_compare": "Размер: %1$s ➔ %2$s",
    "history_savings": "Сэкономлено: %1$s (%2$s)",
    "history_open_file": "Открыть файл",
    "history_share_file": "Поделиться файлом",
    "history_delete_record": "Удалить запись",
    "history_delete_confirm_title": "Удалить эту запись?",
    "history_delete_confirm_body": "Удалить запись из журнала? Сжатый файл на диске останется сохранен.",
    "history_delete_confirm_ok": "Удалить",
    "home_clear_selected": "Очистить выбор",
    "home_estimate_title": "Оценка экономии",
    "home_estimate_info": "Освободится около %1$s",
    "compare_mode_slider": "Ползунок",
    "compare_mode_toggle": "Один экран",
    "compare_label_original": "Оригинал",
    "compare_label_compressed": "Сжатый",
    "compare_press_hint": "Удерживайте экран для быстрого сравнения с оригиналом",
    "countdown_remaining": "Осталось времени:",
    "countdown_elapsed": "Прошло времени:",
    "countdown_seconds_format": "%1$d с",
    "countdown_calculating": "Расчет времени…",
    "auth_network_error": "Ошибка сети, проверьте подключение к интернету.",
    "auth_server_error": "Ошибка сервера (%1$d)",
    "auth_unknown_error": "Неизвестная ошибка, попробуйте позже.",
    "auth_login_title": "Вход в SpaceSlimmer",
    "auth_login_subtitle": "Войдите, чтобы активировать Pro-функции и облачную синхронизацию",
    "auth_phone_hint": "Номер телефона",
    "auth_code_hint": "Код подтверждения",
    "auth_get_code": "Получить код",
    "auth_code_sent": "Код отправлен",
    "auth_login_btn": "Войти",
    "auth_skip_login": "Позже",
    "media_flow_preparing": "Подготовка файлов…",
    "photo_flow_preparing": "Подготовка фотографий…",
    "thumb_load_failed": "Не удалось загрузить миниатюру",
    "app_init_error": "Ошибка инициализации: %1$s",
    "log_viewer_title": "Журнал отладки",
    "log_viewer_copy": "Скопировать журнал",
    "log_viewer_copied": "Журнал скопирован в буфер обмена",
    "log_viewer_clear": "Очистить",
    "log_viewer_empty": "Журнал пуст",
    "preset_video_custom": "Пользовательский",
    "preset_photo_custom": "Пользовательский",
    "preset_audio_custom": "Пользовательский",
    "resolution_original": "Исходное разрешение",
    "resolution_4k": "4K Ultra HD (2160p)",
    "resolution_1080p": "Full HD (1080p)",
    "resolution_720p": "HD (720p)",
    "resolution_480p": "SD (480p)",
    "fps_original": "Исходная частота кадров",
    "fps_60": "60 кадров/с",
    "fps_30": "30 кадров/с",
    "fps_24": "24 кадров/с",
    "codec_h264": "H.264 / AVC (Макс. совместимость)",
    "codec_h265": "H.265 / HEVC (Высокое сжатие)",
    "codec_av1": "AV1 (Передовое ультрасжатие)",
    "audio_bitrate_320k": "320 кбит/с (Студийное)",
    "audio_bitrate_256k": "256 кбит/с (Очень высокое)",
    "audio_bitrate_192k": "192 кбит/с (Высокое)",
    "audio_bitrate_128k": "128 кбит/с (Стандарт)",
    "audio_bitrate_96k": "96 кбит/с (Чистая речь)",
    "audio_bitrate_64k": "64 кбит/с (Макс. экономия)",
    "photo_format_keep": "Сохранить исходный формат",
    "photo_strip_exif": "Удалить метаданные EXIF",
    "photo_keep_exif": "Сохранить метаданные EXIF",
    "dialog_confirm": "Подтвердить",
    "dialog_cancel": "Отмена",
    "dialog_delete": "Удалить",
    "dialog_close": "Закрыть",
    "dialog_done": "Готово",
    "dialog_save": "Сохранить",
    "dialog_apply": "Применить",
    "dialog_warning": "Предупреждение",
    "dialog_info": "Информация",
    "dialog_error": "Ошибка",
    "dialog_success": "Успешно",
    "vip_upgrade_title": "Перейти на SpaceSlimmer Pro",
    "vip_upgrade_subtitle": "Разблокируйте неограниченное аппаратное ускорение и поддержку всех кодеков",
    "vip_feature_batch": "Безлимитное пакетное сжатие",
    "vip_feature_speed": "Ультраскоростной аппаратный движок",
    "vip_feature_codecs": "Поддержка продвинутых кодеков HEVC и AV1",
    "vip_feature_vault": "Увеличенный срок сейфа до 30 дней",
    "vip_feature_clean": "Глубокая очистка дубликатов и кэша",
    "vip_price_monthly": "Месячная подписка: %1$s / мес.",
    "vip_price_yearly": "Годовая подписка: %1$s / год (Скидка 40%%)",
    "vip_price_lifetime": "Пожизненный доступ: %1$s (Разовый платеж)",
    "vip_buy_now": "Оформить подписку",
    "vip_restore_purchase": "Восстановить покупки",
    "vip_status_active": "Статус Pro активен",
    "vip_status_expires": "Истекает: %1$s",
    "vip_status_lifetime": "Пожизненная лицензия",
    "vip_terms": "Условия использования и политика конфиденциальности",
    "cloud_sync_title": "Облачная синхронизация",
    "cloud_sync_subtitle": "Безопасное резервное копирование в облако",
    "cloud_sync_now": "Синхронизировать сейчас",
    "cloud_sync_in_progress": "Синхронизация… (%1$d%%)",
    "cloud_sync_completed": "Синхронизация завершена",
    "cloud_storage_used": "Облако: %1$s / %2$s",
    "cloud_upgrade_storage": "Увеличить объем хранилища",
    "cloud_auto_sync": "Автосинхронизация по Wi-Fi",
    "cloud_file_count": "В облаке сохранено %1$d файлов",
    "clean_scan_button": "Сканировать память",
    "clean_scanning_label": "Поиск ненужных файлов…",
    "clean_total_scanned": "Всего найдено: %1$s",
    "clean_button_text": "Очистить %1$s сейчас",
    "clean_done_title": "Очистка завершена!",
    "clean_done_message": "Поздравляем, освобождено %1$s памяти.",
    "clean_details_category": "Категории очистки",
    "clean_cat_app_cache": "Кэш приложений",
    "clean_cat_temp_files": "Временные файлы",
    "clean_cat_empty_dirs": "Пустые папки",
    "clean_cat_large_videos": "Большие видеофайлы",
    "clean_cat_duplicate_photos": "Дубликаты фото",
    "clean_cat_blurred_photos": "Размытые фото",
    "clean_cat_screenshots": "Скриншоты",
    "clean_cat_old_apks": "Устаревшие пакеты APK",
    "clean_cat_audio_cache": "Кэш аудио",
    "clean_confirm_delete_cat": "Навсегда удалить %1$d файлов из этой категории?",
    "err_no_media_selected": "Пожалуйста, выберите хотя бы один файл для обработки.",
    "err_insufficient_storage": "Недостаточно места на устройстве. Освободите память и повторите попытку.",
    "err_file_not_found": "Выбранный файл не найден или был перемещен.",
    "err_codec_unsupported": "Кодек файла не поддерживается вашим устройством.",
    "err_read_permission_denied": "В разрешении на чтение файлов отказано.",
    "err_write_permission_denied": "В разрешении на запись файлов отказано.",
    "err_compress_interrupted": "Операция была отменена пользователем.",
    "err_invalid_output_format": "Выбран недопустимый выходной формат.",
    "err_unknown": "Произошла непредвиденная ошибка: %1$s",
    "settings_theme": "Тема оформления",
    "settings_theme_system": "Как в системе",
    "settings_theme_light": "Светлая",
    "settings_theme_dark": "Темная",
    "settings_notifications": "Уведомления о завершении задач",
    "settings_sound_effects": "Звуковые эффекты",
    "settings_vibration": "Виброотклик",
    "settings_auto_clean_cache": "Автоочистка временного кэша",
    "settings_check_update": "Проверить обновления",
    "settings_update_available": "Доступна новая версия: %1$s",
    "settings_latest_version": "У вас установлена последняя версия (%1$s)",
    "settings_feedback": "Отзывы и предложения",
    "settings_rate_app": "Оценить приложение",
    "settings_privacy_policy": "Политика конфиденциальности",
    "settings_user_agreement": "Пользовательское соглашение",
    "settings_contact_support": "Связаться с поддержкой",
    "settings_export_logs": "Экспорт журналов отладки",
    "settings_open_source_licenses": "Лицензии с открытым исходным кодом",
    "batch_select_hint": "Выберите элементы для обработки",
    "batch_invert_selection": "Инвертировать выбор",
    "batch_items_selected_count": "Выбрано %1$d элементов",
    "filter_all": "Все",
    "filter_images": "Фото",
    "filter_videos": "Видео",
    "filter_audios": "Аудио",
    "filter_large_files": "Файлы > 100 МБ",
    "sort_by_date_desc": "По дате (сначала новые)",
    "sort_by_date_asc": "По дате (сначала старые)",
    "sort_by_size_desc": "По размеру (сначала большие)",
    "sort_by_size_asc": "По размеру (сначала маленькие)",
    "sort_by_name": "По имени файла",
    "time_just_now": "Только что",
    "time_minutes_ago": "%1$d мин. назад",
    "time_hours_ago": "%1$d ч. назад",
    "time_days_ago": "%1$d дн. назад",
    "time_today": "Сегодня",
    "time_yesterday": "Вчера",
    "status_idle": "Ожидание",
    "status_processing": "Обработка…",
    "status_completed": "Завершено",
    "status_failed": "Ошибка",
    "status_paused": "Приостановлено",
    "status_cancelled": "Отменено",
    "file_size_b": "%1$d Б",
    "file_size_kb": "%1$.1f КБ",
    "file_size_mb": "%1$.1f МБ",
    "file_size_gb": "%1$.2f ГБ",
    "prompt_save_preset": "Сохранить как новый пресет",
    "prompt_preset_name": "Имя пресета",
    "prompt_preset_saved": "Пресет успешно сохранен",
    "prompt_preset_deleted": "Пресет удален",
    "warning_low_battery": "Низкий заряд батареи (< 15%%). Сжатие видео может быстро разрядить аккумулятор.",
    "warning_thermal_throttle": "Устройство нагрелось. Производительность сжатия может быть временно снижена.",
    "notification_channel_compression": "Задачи сжатия",
    "notification_channel_clean": "Очистка файлов",
    "notification_compressing_title": "Сжатие %1$d файлов…",
    "notification_compressing_body": "Прогресс: %1$d%% · Сэкономлено: %2$s",
    "notification_done_title": "Сжатие успешно завершено!",
    "notification_done_body": "Обработано %1$d файлов, освобождено %2$s памяти.",
    "notification_clean_title": "Радар очистки",
    "notification_clean_body": "Можно освободить %1$s ненужных файлов прямо сейчас.",
    "share_title": "Поделиться сжатыми файлами",
    "share_chooser_title": "Отправить через",
    "save_to_custom_folder": "Сохранить в другую папку",
    "save_success_path": "Успешно сохранено в: %1$s",
    "permission_storage_rationale": "SpaceSlimmer требуется доступ к фото и видео для сжатия и освобождения места.",
    "permission_grant_button": "Предоставить доступ",
    "permission_denied_toast": "В доступе отказано, невозможно загрузить файлы",
    "permission_settings_button": "Открыть настройки",
}

tree_zh = ET.parse('android/app/src/main/res/values-zh/strings.xml')
tree_en = ET.parse('android/app/src/main/res/values-en/strings.xml')
en_map = {elem.attrib['name']: elem.text or "" for elem in tree_en.getroot().findall('string')}
zh_map = {elem.attrib['name']: elem.text or "" for elem in tree_zh.getroot().findall('string')}

final_map = {}
for k in all_keys:
    if k in RU_TRANSLATIONS:
        final_map[k] = RU_TRANSLATIONS[k]
    else:
        final_map[k] = en_map.get(k, k)

py_code = ['# -*- coding: utf-8 -*-', 'import xml.etree.ElementTree as ET', 'import os', '', 'RU_MAP = {']
for k in all_keys:
    val = final_map[k].replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '')
    py_code.append(f'    "{k}": "{val}",')
py_code.append('}')
py_code.append(r'''
def escape_xml(val):
    if not val:
        return ""
    val = val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    parts = val.split(r"\'")
    val = r"\'".join([p.replace("'", r"\'") for p in parts])
    parts = val.split(r'\"')
    val = r'\"'.join([p.replace('"', r'\"') for p in parts])
    return val

def run():
    tree_base = ET.parse('android/app/src/main/res/values/strings.xml')
    keys = [elem.attrib['name'] for elem in tree_base.getroot().findall('string')]
    base_map = {elem.attrib['name']: elem.text or "" for elem in tree_base.getroot().findall('string')}

    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for k in keys:
        val = RU_MAP.get(k, base_map.get(k, ""))
        esc = escape_xml(val)
        lines.append(f'    <string name="{k}">{esc}</string>')

    # Russian plurals (one, few, many, other)
    lines.append('    <plurals name="selected_photos_count">')
    lines.append('        <item quantity="one">Выбрана %1$d фотография</item>')
    lines.append('        <item quantity="few">Выбрано %1$d фотографии</item>')
    lines.append('        <item quantity="many">Выбрано %1$d фотографий</item>')
    lines.append('        <item quantity="other">Выбрано %1$d фото</item>')
    lines.append('    </plurals>')
    lines.append('    <plurals name="selected_files_count">')
    lines.append('        <item quantity="one">Выбран %1$d файл</item>')
    lines.append('        <item quantity="few">Выбрано %1$d файла</item>')
    lines.append('        <item quantity="many">Выбрано %1$d файлов</item>')
    lines.append('        <item quantity="other">Выбрано %1$d файлов</item>')
    lines.append('    </plurals>')
    lines.append('    <plurals name="saved_to_gallery_count">')
    lines.append('        <item quantity="one">%1$d сохранен в альбоме SpaceSlimmer</item>')
    lines.append('        <item quantity="few">%1$d сохранено в альбоме SpaceSlimmer</item>')
    lines.append('        <item quantity="many">%1$d сохранено в альбоме SpaceSlimmer</item>')
    lines.append('        <item quantity="other">%1$d сохранено в альбоме SpaceSlimmer</item>')
    lines.append('    </plurals>')
    lines.append('    <plurals name="replaced_gallery_count">')
    lines.append('        <item quantity="one">Заменен %1$d оригинал</item>')
    lines.append('        <item quantity="few">Заменено %1$d оригинала</item>')
    lines.append('        <item quantity="many">Заменено %1$d оригиналов</item>')
    lines.append('        <item quantity="other">Заменено %1$d оригиналов</item>')
    lines.append('    </plurals>')
    lines.append('    <plurals name="vault_expiring_days">')
    lines.append('        <item quantity="one">Срок действия %1$d резервной копии сейфа истекает менее чем через 3 дня</item>')
    lines.append('        <item quantity="few">Срок действия %1$d резервных копий сейфа истекает менее чем через 3 дня</item>')
    lines.append('        <item quantity="many">Срок действия %1$d резервных копий сейфа истекает менее чем через 3 дня</item>')
    lines.append('        <item quantity="other">Срок действия %1$d резервных копий сейфа истекает менее чем через 3 дня</item>')
    lines.append('    </plurals>')
    lines.append('</resources>\n')

    out_dir = 'android/app/src/main/res/values-ru'
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, 'strings.xml'), 'w', encoding='utf-8') as f:
        f.write("\n".join(lines))
    print(f"Generated Russian (ru) with {len(keys)} keys.")

if __name__ == '__main__':
    run()
''')

with open('scripts/locales/gen_ru.py', 'w', encoding='utf-8') as f:
    f.write("\n".join(py_code))

print("Created scripts/locales/gen_ru.py with", len(all_keys), "keys.")
